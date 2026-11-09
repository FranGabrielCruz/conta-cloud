package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.dto.ActivoFijoDto.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class ActivoFijoRepository {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String EMPRESA = "tenant_id=:tenant AND empresa_id=:empresa";
    private static final String SUCURSAL = "(:todas OR a.sucursal_id IN (:sucursales))";
    private static final String ORIGEN = """
        FROM activo_fijo a
        JOIN activo_fijo_categoria c ON c.codigo=a.categoria
        JOIN sucursales s ON s.tenant_id=a.tenant_id AND s.empresa_id=a.empresa_id AND s.id=a.sucursal_id
        JOIN monedas m ON m.tenant_id=a.tenant_id AND m.empresa_id=a.empresa_id AND m.id=a.moneda_id
        LEFT JOIN usuarios u ON u.tenant_id=a.tenant_id AND u.id=a.responsable_id
        LEFT JOIN supplier p ON p.tenant_id=a.tenant_id AND p.empresa_id=a.empresa_id AND p.id=a.proveedor_id
        LEFT JOIN purchase_invoice f ON f.tenant_id=a.tenant_id AND f.empresa_id=a.empresa_id AND f.id=a.factura_id
            AND (:todas OR f.branch_id IN (:sucursales))
        LEFT JOIN LATERAL (
            SELECT COALESCE(sum(importe) FILTER (WHERE tipo='DEPRECIACION'),0) depreciacion,
                   COALESCE(sum(importe) FILTER (WHERE tipo='AJUSTE'),0) ajustes,
                   count(*) FILTER (WHERE tipo IN ('DEPRECIACION','AJUSTE','BAJA')) > 0 bloqueado,
                   bool_or(tipo='BAJA') baja
            FROM activo_fijo_movimiento mv
            WHERE mv.tenant_id=a.tenant_id AND mv.empresa_id=a.empresa_id AND mv.activo_id=a.id
        ) mov ON true
        """;
    private static final String AMBITO = " WHERE a.tenant_id=:tenant AND a.empresa_id=:empresa AND " + SUCURSAL;
    private static final String ESTADO = "(CASE WHEN mov.baja THEN 'BAJA' ELSE a.estado END)";
    private static final String LIBROS = "(CASE WHEN " + ESTADO + "='BAJA' THEN 0 ELSE a.costo-mov.depreciacion-mov.ajustes END)";
    private static final String FILTROS = """
         AND (CAST(:categoria AS varchar) IS NULL OR a.categoria=:categoria)
         AND (CAST(:sucursal AS uuid) IS NULL OR a.sucursal_id=:sucursal)
         AND (CAST(:ubicacion AS varchar) IS NULL OR a.ubicacion=:ubicacion)
         AND (CAST(:estado AS varchar) IS NULL OR %s=:estado)
         AND (:buscar='' OR strpos(lower(concat_ws(' ',a.codigo,a.nombre,a.marca,a.modelo,a.serie,u.nombre,u.apellido)),lower(:buscar))>0)
        """.formatted(ESTADO);
    private static final String COLUMNAS = """
        SELECT a.*, c.nombre categoria_nombre, s.nombre sucursal_nombre, m.codigo_iso moneda,
               NULLIF(concat_ws(' ',u.nombre,NULLIF(u.apellido,'')),'') responsable_nombre,
               p.trade_name proveedor_nombre, f.internal_number || ' · ' || f.supplier_invoice_number factura_numero,
               mov.depreciacion, mov.bloqueado, %s estado_actual, %s libros
        """.formatted(ESTADO, LIBROS);
    private final NamedParameterJdbcTemplate jdbc;

    public ActivoFijoRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Resumen> listar(TenantPrincipal p, Filtro f, Long offset, int limite) {
        var params = filtro(p,f);
        String pagina = offset == null ? "" : " LIMIT :limite OFFSET :offset";
        params.addValue("offset",offset).addValue("limite",limite);
        return jdbc.query(COLUMNAS + ORIGEN + AMBITO + FILTROS + " ORDER BY a.codigo,a.id" + pagina,
            params,(rs,n)->resumen(rs));
    }

    public List<Totales> totales(TenantPrincipal p, Filtro f) {
        return jdbc.query("""
            SELECT m.codigo_iso moneda,count(*) cantidad,
                   count(*) FILTER (WHERE %s='ACTIVO') vigentes,
                   count(*) FILTER (WHERE %s<>'BAJA' AND a.condicion='MANTENIMIENTO') mantenimiento,
                   sum(a.costo) costo,sum(mov.depreciacion) depreciacion,sum(%s) libros
            """.formatted(ESTADO,ESTADO,LIBROS) + ORIGEN + AMBITO + FILTROS + " GROUP BY m.codigo_iso ORDER BY m.codigo_iso",
            filtro(p,f),(rs,n)->new Totales(rs.getString("moneda"),rs.getLong("cantidad"),
                rs.getLong("vigentes"),rs.getLong("mantenimiento"),rs.getBigDecimal("costo"),
                rs.getBigDecimal("depreciacion"),rs.getBigDecimal("libros")));
    }

    public Optional<Registro> registro(TenantPrincipal p, UUID id) {
        return jdbc.query(COLUMNAS + ORIGEN + AMBITO + " AND a.id=:id", alcance(p).addValue("id",id),
            (rs,n)->registro(rs)).stream().findFirst();
    }

    public boolean bloquear(TenantPrincipal p, UUID id) {
        return !jdbc.query("SELECT a.id FROM activo_fijo a" + AMBITO + " AND a.id=:id FOR UPDATE",
            alcance(p).addValue("id",id),(rs,n)->uuid(rs,"id")).isEmpty();
    }

    public List<HistorialAsignacion> asignaciones(TenantPrincipal p, UUID id) {
        return jdbc.query("""
            SELECT fecha,usuario_nombre,anterior,nueva FROM activo_fijo_asignacion
            WHERE %s AND activo_id=:id AND (:todas OR (sucursal_id IN (:sucursales)
                AND (sucursal_anterior_id IS NULL OR sucursal_anterior_id IN (:sucursales))))
            ORDER BY version DESC
            """.formatted(EMPRESA),alcance(p).addValue("id",id),(rs,n)->new HistorialAsignacion(
                rs.getObject("fecha",OffsetDateTime.class),rs.getString("usuario_nombre"),
                destino(rs.getString("anterior")),destino(rs.getString("nueva"))));
    }

    public List<Movimiento> movimientos(TenantPrincipal p, UUID id) {
        return jdbc.query("""
            SELECT tipo,fecha,descripcion,importe FROM activo_fijo_movimiento
            WHERE %s AND activo_id=:id AND (:todas OR sucursal_id IN (:sucursales))
            ORDER BY fecha DESC,id
            """.formatted(EMPRESA),alcance(p).addValue("id",id),(rs,n)->new Movimiento(
                MovimientoTipo.valueOf(rs.getString("tipo")),rs.getObject("fecha",LocalDate.class),
                rs.getString("descripcion"),rs.getBigDecimal("importe")));
    }

    public Catalogos catalogos(TenantPrincipal p, boolean formulario) {
        var params = alcance(p);
        var categorias = jdbc.query("SELECT codigo,nombre FROM activo_fijo_categoria ORDER BY nombre",
            params,(rs,n)->new Categoria(rs.getString(1),rs.getString(2)));
        var sucursales = opciones("""
            SELECT id,nombre FROM sucursales WHERE tenant_id=:tenant AND empresa_id=:empresa AND (:historico OR activo)
                AND (:todas OR id IN (:sucursales)) ORDER BY principal DESC,nombre
            """,params.addValue("historico",!formulario));
        var ubicaciones = jdbc.queryForList("SELECT DISTINCT a.ubicacion FROM activo_fijo a" + AMBITO
            + " AND a.ubicacion IS NOT NULL ORDER BY a.ubicacion",params,String.class);
        if (!formulario) return new Catalogos(categorias,sucursales,ubicaciones,List.of(),List.of(),List.of());

        var monedas = jdbc.query("""
            SELECT id,codigo_iso,moneda_base FROM monedas WHERE tenant_id=:tenant AND empresa_id=:empresa AND activo
            ORDER BY moneda_base DESC,codigo_iso
            """,params,(rs,n)->new Moneda(uuid(rs,"id"),rs.getString("codigo_iso"),rs.getBoolean("moneda_base")));
        var proveedores = opciones("SELECT id,trade_name FROM supplier WHERE " + EMPRESA + " AND active ORDER BY trade_name",params);
        var responsables = opciones("""
            SELECT u.id,concat_ws(' ',u.nombre,NULLIF(u.apellido,'')) nombre
            FROM usuario_empresa ue JOIN usuarios u ON u.id=ue.usuario_id
            WHERE ue.empresa_id=:empresa AND u.tenant_id=:tenant AND ue.activo AND u.activo
            ORDER BY nombre,u.id
            """,params);
        return new Catalogos(categorias,sucursales,ubicaciones,monedas,proveedores,responsables);
    }

    public List<Factura> facturas(TenantPrincipal p, UUID proveedor, String buscar, int offset, int limite) {
        return jdbc.query("""
            SELECT id,supplier_id,internal_number || ' · ' || supplier_invoice_number numero FROM purchase_invoice
            WHERE %s AND supplier_id=:proveedor AND status='REGISTERED' AND (:todas OR branch_id IN (:sucursales))
                AND (:buscar='' OR strpos(lower(internal_number || ' ' || supplier_invoice_number),lower(:buscar))>0)
            ORDER BY invoice_date DESC,id LIMIT :limite OFFSET :offset
            """.formatted(EMPRESA),alcance(p).addValue("proveedor",proveedor).addValue("buscar",buscar)
                .addValue("offset",offset).addValue("limite",limite),
            (rs,n)->new Factura(uuid(rs,"id"),uuid(rs,"supplier_id"),rs.getString("numero")));
    }

    public boolean facturaValida(TenantPrincipal p, Adquisicion a) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM purchase_invoice WHERE %s AND id=:id AND supplier_id=:proveedor
                AND currency_id=:moneda AND status='REGISTERED' AND (:todas OR branch_id IN (:sucursales)))
            """.formatted(EMPRESA),alcance(p).addValue("id",a.factura()).addValue("proveedor",a.proveedor())
                .addValue("moneda",a.moneda()),Boolean.class));
    }

    public String siguienteCodigo(TenantPrincipal p) {
        var numero = jdbc.queryForObject("""
            INSERT INTO activo_fijo_secuencia(tenant_id,empresa_id,ultimo) VALUES(:tenant,:empresa,1)
            ON CONFLICT(tenant_id,empresa_id) DO UPDATE SET ultimo=activo_fijo_secuencia.ultimo+1
            RETURNING ultimo
            """,alcance(p),Long.class);
        return String.format(Locale.ROOT,"AF-%06d",numero);
    }

    public void insertar(TenantPrincipal p, UUID id, String codigo, Datos d) {
        jdbc.update("""
            INSERT INTO activo_fijo(id,tenant_id,empresa_id,codigo,nombre,categoria,marca,modelo,serie,
                fecha_adquisicion,costo,moneda_id,proveedor_id,factura_id,referencia_compra,sucursal_id,ubicacion,
                responsable_id,depreciable,metodo,vida_meses,residual,inicio_depreciacion,observacion)
            VALUES(:id,:tenant,:empresa,:codigo,:nombre,:categoria,:marca,:modelo,:serie,
                :fecha,:costo,:moneda,:proveedor,:factura,:referencia,:sucursal,:ubicacion,
                :responsable,:depreciable,:metodo,:vida,:residual,:inicio,:observacion)
            """,datos(p,d).addValue("id",id).addValue("codigo",codigo));
    }

    public boolean actualizar(TenantPrincipal p, UUID id, long version, Datos d) {
        return jdbc.update("""
            UPDATE activo_fijo a SET nombre=:nombre,categoria=:categoria,marca=:marca,modelo=:modelo,serie=:serie,
                fecha_adquisicion=:fecha,costo=:costo,moneda_id=:moneda,proveedor_id=:proveedor,factura_id=:factura,
                referencia_compra=:referencia,sucursal_id=:sucursal,ubicacion=:ubicacion,responsable_id=:responsable,
                depreciable=:depreciable,metodo=:metodo,vida_meses=:vida,residual=:residual,inicio_depreciacion=:inicio,
                observacion=:observacion,version=version+1
            """ + AMBITO + " AND a.id=:id AND a.version=:version",
            datos(p,d).addValue("id",id).addValue("version",version)) == 1;
    }

    public void guardarAsignacion(TenantPrincipal p, Registro anterior, Registro actual) {
        var destinoAnterior = anterior == null ? null : anterior.destino();
        var sucursalAnterior = anterior == null ? null : anterior.datos().asignacion().sucursal();
        jdbc.update("""
            INSERT INTO activo_fijo_asignacion(tenant_id,empresa_id,activo_id,version,sucursal_anterior_id,sucursal_id,
                anterior,nueva,usuario_id,usuario_nombre)
            VALUES(:tenant,:empresa,:activo,:version,:sucursalAnterior,:sucursal,
                CAST(:anterior AS jsonb),CAST(:nueva AS jsonb),:usuario,:nombre)
            """,alcance(p).addValue("activo",actual.resumen().id()).addValue("version",actual.version())
                .addValue("sucursalAnterior",sucursalAnterior).addValue("sucursal",actual.datos().asignacion().sucursal())
                .addValue("anterior",destinoAnterior==null?null:JSON.writeValueAsString(destinoAnterior))
                .addValue("nueva",JSON.writeValueAsString(actual.destino()))
                .addValue("usuario",p.usuarioId()).addValue("nombre",p.nombre()));
    }

    private List<Opcion> opciones(String sql, MapSqlParameterSource p) {
        return jdbc.query(sql,p,(rs,n)->new Opcion(rs.getObject(1,UUID.class),rs.getString(2)));
    }

    private static Resumen resumen(ResultSet r) throws SQLException {
        var estado=Estado.valueOf(r.getString("estado_actual"));
        String condicion = estado==Estado.BAJA?"Fuera de servicio":switch (r.getString("condicion")) {
            case "MANTENIMIENTO" -> "En mantenimiento";
            case "FUERA_SERVICIO" -> "Fuera de servicio";
            case null, default -> uuid(r,"responsable_id")==null?"Disponible":"Asignado";
        };
        return new Resumen(uuid(r,"id"),r.getString("codigo"),r.getString("nombre"),r.getString("categoria_nombre"),
            r.getString("sucursal_nombre"),r.getString("ubicacion"),estado,condicion,
            r.getObject("fecha_adquisicion",LocalDate.class),r.getString("moneda"),r.getBigDecimal("costo"),
            r.getBigDecimal("depreciacion"),r.getBigDecimal("libros"));
    }

    private static Registro registro(ResultSet r) throws SQLException {
        var resumen = resumen(r);
        var adquisicion = new Adquisicion(resumen.fecha(),resumen.costo(),uuid(r,"moneda_id"),
            uuid(r,"proveedor_id"),uuid(r,"factura_id"),r.getString("referencia_compra"));
        var asignacion = new Asignacion(uuid(r,"sucursal_id"),resumen.ubicacion(),uuid(r,"responsable_id"));
        var metodo = r.getString("metodo");
        var depreciacion = new Depreciacion(r.getBoolean("depreciable"),metodo==null?null:Metodo.valueOf(metodo),
            r.getObject("vida_meses",Integer.class),r.getBigDecimal("residual"),r.getObject("inicio_depreciacion",LocalDate.class));
        var datos = new Datos(resumen.nombre(),r.getString("categoria"),r.getString("marca"),r.getString("modelo"),
            r.getString("serie"),adquisicion,asignacion,depreciacion,r.getString("observacion"));
        return new Registro(resumen,r.getLong("version"),datos,
            new Destino(resumen.sucursal(),resumen.ubicacion(),r.getString("responsable_nombre")),
            r.getString("proveedor_nombre"),r.getString("factura_numero"),r.getBoolean("bloqueado"));
    }

    private static UUID uuid(ResultSet r,String col) throws SQLException { return r.getObject(col,UUID.class); }
    private static Destino destino(String json) { return json==null?null:JSON.readValue(json,Destino.class); }

    private static MapSqlParameterSource alcance(TenantPrincipal p) {
        return new MapSqlParameterSource("tenant",p.tenantId()).addValue("empresa",p.empresaId())
            .addValue("todas",p.accesoTodasSucursales())
            .addValue("sucursales",p.sucursalIds().isEmpty()?List.of(new UUID(0,0)):p.sucursalIds());
    }

    private static MapSqlParameterSource filtro(TenantPrincipal p,Filtro f) {
        return alcance(p).addValue("categoria",f.categoria()).addValue("sucursal",f.sucursal())
            .addValue("ubicacion",f.ubicacion()).addValue("estado",f.estado()==null?null:f.estado().name())
            .addValue("buscar",f.buscar());
    }

    private static MapSqlParameterSource datos(TenantPrincipal p, Datos d) {
        var a=d.adquisicion(); var s=d.asignacion(); var dep=d.depreciacion();
        return alcance(p).addValue("nombre",d.nombre()).addValue("categoria",d.categoria()).addValue("marca",d.marca())
            .addValue("modelo",d.modelo()).addValue("serie",d.serie()).addValue("fecha",a.fecha()).addValue("costo",a.costo())
            .addValue("moneda",a.moneda()).addValue("proveedor",a.proveedor()).addValue("factura",a.factura())
            .addValue("referencia",a.referencia()).addValue("sucursal",s.sucursal()).addValue("ubicacion",s.ubicacion())
            .addValue("responsable",s.responsable()).addValue("depreciable",dep.depreciable())
            .addValue("metodo",dep.metodo()==null?null:dep.metodo().name()).addValue("vida",dep.vidaMeses())
            .addValue("residual",dep.residual()).addValue("inicio",dep.inicio()).addValue("observacion",d.observacion());
    }
}
