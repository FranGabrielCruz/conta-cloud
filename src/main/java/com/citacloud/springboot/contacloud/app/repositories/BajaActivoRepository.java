package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.dto.BajaActivoDto.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class BajaActivoRepository {
    private static final JsonMapper JSON=JsonMapper.builder().findAndAddModules().build();
    private static final String AMBITO="tenant_id=:tenant AND empresa_id=:empresa";
    private static final String BAJA="b.tenant_id=:tenant AND b.empresa_id=:empresa AND (:todas OR b.sucursal_id IN (:sucursales))";
    private static final String ACTIVO="a.tenant_id=:tenant AND a.empresa_id=:empresa AND (:todas OR a.sucursal_id IN (:sucursales))";
    private static final String RETIRADO="""
        (a.estado='BAJA' OR EXISTS (SELECT 1 FROM activo_fijo_movimiento m WHERE m.tenant_id=a.tenant_id
            AND m.empresa_id=a.empresa_id AND m.activo_id=a.id AND m.tipo='BAJA'))
        """.trim();
    private static final String ORIGEN="""
        FROM activo_baja b JOIN activo_fijo a ON a.tenant_id=b.tenant_id AND a.empresa_id=b.empresa_id AND a.id=b.activo_id
        JOIN activo_baja_tipo t ON t.codigo=b.tipo JOIN monedas c ON c.empresa_id=b.empresa_id AND c.id=b.moneda_id
        """;
    private final NamedParameterJdbcTemplate jdbc;

    public BajaActivoRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }

    public List<Tipo> tipos() {
        return jdbc.query("SELECT codigo,nombre FROM activo_baja_tipo ORDER BY nombre",(r,n)->new Tipo(r.getString(1),r.getString(2)));
    }

    public Page<Fila> listar(TenantPrincipal p,Filtro f,Pageable pagina) {
        var args=alcance(p).addValue("desde",f.desde()).addValue("hasta",f.hasta()).addValue("tipo",f.tipo())
            .addValue("estado",f.estado()==null?null:f.estado().name()).addValue("buscar",f.buscar())
            .addValue("offset",pagina.getOffset()).addValue("limite",pagina.getPageSize());
        String origen=ORIGEN+" WHERE "+BAJA+"""
             AND (b.estado='CONFIRMADA' OR :todas OR a.sucursal_id IN (:sucursales))
             AND (CAST(:desde AS date) IS NULL OR b.fecha>=:desde) AND (CAST(:hasta AS date) IS NULL OR b.fecha<=:hasta)
             AND (CAST(:tipo AS varchar) IS NULL OR b.tipo=:tipo) AND (CAST(:estado AS varchar) IS NULL OR b.estado=:estado)
             AND (:buscar='' OR strpos(lower(concat_ws(' ',b.codigo, CASE WHEN b.estado='CONFIRMADA' THEN
               concat_ws(' ',b.snapshot->'activo'->>'codigo',b.snapshot->'activo'->>'nombre',
               b.snapshot->'activo'->>'marca',b.snapshot->'activo'->>'modelo',b.snapshot->'activo'->>'serie')
               ELSE concat_ws(' ',a.codigo,a.nombre,a.marca,a.modelo,a.serie) END)),lower(:buscar))>0)
            """;
        var filas=jdbc.query("""
            SELECT b.id,b.codigo,COALESCE(b.snapshot->'activo'->>'codigo',a.codigo)||' — '||
                COALESCE(b.snapshot->'activo'->>'nombre',a.nombre) activo,
                b.fecha,COALESCE(b.snapshot->>'tipo',t.nombre) tipo,b.libros,
                COALESCE(b.snapshot->'activo'->>'moneda',c.codigo_iso) moneda,b.estado
            """+origen+" ORDER BY b.fecha DESC,b.codigo DESC LIMIT :limite OFFSET :offset",
            args,(r,n)->new Fila(uuid(r,"id"),r.getString("codigo"),r.getString("activo"),r.getObject("fecha",LocalDate.class),
                r.getString("tipo"),r.getBigDecimal("libros"),r.getString("moneda"),Estado.valueOf(r.getString("estado"))));
        long total=jdbc.queryForObject("SELECT count(*) "+origen,args,Long.class);
        return new PageImpl<>(filas,pagina,total);
    }

    public List<Total> totales(TenantPrincipal p,OffsetDateTime mes,OffsetDateTime fin) {
        return jdbc.query("""
            SELECT b.snapshot->'activo'->>'moneda',count(*),sum(b.costo),sum(b.libros)
            FROM activo_baja b WHERE
            """+BAJA+" AND b.estado='CONFIRMADA' AND b.confirmado_en>=:mes AND b.confirmado_en<:fin GROUP BY 1 ORDER BY 1",
            alcance(p).addValue("mes",mes).addValue("fin",fin),
            (r,n)->new Total(r.getString(1),r.getLong(2),r.getBigDecimal(3),r.getBigDecimal(4)));
    }

    public List<Activo> buscarActivos(TenantPrincipal p,String texto,int offset,int limite,LocalDate fecha) {
        return jdbc.query(columnasActivo()+origenActivo()+" AND NOT "+RETIRADO+"""
             AND (:buscar='' OR strpos(lower(concat_ws(' ',a.codigo,a.nombre,a.marca,a.modelo,a.serie)),lower(:buscar))>0)
             ORDER BY a.codigo LIMIT :limite OFFSET :offset
            """,alcance(p).addValue("fecha",fecha).addValue("buscar",texto).addValue("offset",offset).addValue("limite",limite),
            (r,n)->activo(r));
    }

    public Optional<Activo> activo(TenantPrincipal p,UUID id,LocalDate fecha,boolean bloquear) {
        var args=alcance(p).addValue("id",id).addValue("fecha",fecha);
        if(bloquear && jdbc.query("SELECT a.id FROM activo_fijo a WHERE "+ACTIVO+" AND a.id=:id FOR UPDATE",
                args,(r,n)->uuid(r,"id")).isEmpty()) return Optional.empty();
        return jdbc.query(columnasActivo()+origenActivo()+" AND a.id=:id",args,(r,n)->activo(r)).stream().findFirst();
    }

    private static String columnasActivo() {
        return """
            SELECT a.*,c.codigo_iso moneda,k.nombre categoria_nombre,COALESCE(m.dep,0) depreciacion,
                COALESCE(m.ajustes,0) ajustes,m.ultima,(COALESCE(m.posterior,false)
                    OR EXISTS(SELECT 1 FROM activo_mantenimiento x WHERE x.tenant_id=a.tenant_id
                        AND x.empresa_id=a.empresa_id AND x.activo_id=a.id AND x.estado='COMPLETADO' AND x.realizada>:fecha)
                ) posterior,
                %s retirado, EXISTS(SELECT 1 FROM activo_mantenimiento x WHERE x.tenant_id=a.tenant_id
                    AND x.empresa_id=a.empresa_id AND x.activo_id=a.id AND x.estado IN ('PROGRAMADO','EN_PROCESO')) mantenimiento
            """.formatted(RETIRADO);
    }

    private static String origenActivo() {
        return """
            FROM activo_fijo a JOIN monedas c ON c.empresa_id=a.empresa_id AND c.id=a.moneda_id
            JOIN activo_fijo_categoria k ON k.codigo=a.categoria
            LEFT JOIN LATERAL (
                SELECT sum(importe) FILTER(WHERE tipo='DEPRECIACION' AND fecha<=:fecha) dep,
                    sum(importe) FILTER(WHERE tipo='AJUSTE' AND fecha<=:fecha) ajustes,
                    max(fecha) FILTER(WHERE tipo='DEPRECIACION' AND fecha<=:fecha) ultima,
                    bool_or(tipo IN ('DEPRECIACION','AJUSTE','MANTENIMIENTO') AND fecha>:fecha) posterior
                FROM activo_fijo_movimiento v WHERE v.tenant_id=a.tenant_id AND v.empresa_id=a.empresa_id AND v.activo_id=a.id
            ) m ON true WHERE
            """+ACTIVO;
    }

    public Optional<Detalle> detalle(TenantPrincipal p,UUID id,boolean bloqueo) {
        return buscarDetalle(p," AND b.id=:id",alcance(p).addValue("id",id),bloqueo);
    }

    public Optional<Detalle> confirmada(TenantPrincipal p,UUID activo) {
        return buscarDetalle(p," AND b.activo_id=:id AND b.estado='CONFIRMADA'",alcance(p).addValue("id",activo),false);
    }

    private Optional<Detalle> buscarDetalle(TenantPrincipal p,String condicion,MapSqlParameterSource args,boolean bloqueo) {
        String origen = ORIGEN + """
            LEFT JOIN sales_invoice f ON f.tenant_id=b.tenant_id AND f.empresa_id=b.empresa_id AND f.id=b.factura_id
                AND (:todas OR f.branch_id IN (:sucursales))
            WHERE
            """;
        var base=jdbc.query("SELECT b.*,t.nombre tipo_nombre,f.internal_number factura_codigo "+origen+BAJA
            +" AND (b.estado='CONFIRMADA' OR :todas OR a.sucursal_id IN (:sucursales))"+condicion+(bloqueo?" FOR UPDATE OF b":""),
            args,(r,n)->{
                var snapshot=r.getString("snapshot");
                var entrada=new Entrada(uuid(r,"activo_id"),r.getString("tipo"),r.getObject("fecha",LocalDate.class),r.getString("motivo"),
                    r.getBigDecimal("obtenido"),r.getString("referencia"),uuid(r,"factura_id"),r.getString("observacion"));
                return new Detalle(uuid(r,"id"),uuid(r,"sucursal_id"),r.getString("codigo"),Estado.valueOf(r.getString("estado")),
                    r.getLong("version"),entrada,r.getString("tipo_nombre"),r.getString("factura_codigo"),null,
                    snapshot==null?null:JSON.readValue(snapshot,Snapshot.class),
                    r.getObject("confirmado_en",OffsetDateTime.class),uuid(r,"confirmado_por"),uuid(r,"solicitud_id"));
            }).stream().findFirst();
        return base.map(d->new Detalle(d.id(),d.sucursal(),d.codigo(),d.estado(),d.version(),d.datos(),d.tipo(),
            d.snapshot()==null?d.factura():d.snapshot().factura(),
            d.snapshot()!=null?d.snapshot().activo():activo(p,d.datos().activo(),d.datos().fecha(),false).orElse(null),
            d.snapshot(),d.confirmadoEn(),d.confirmadoPor(),d.solicitud()));
    }

    public List<Factura> facturas(TenantPrincipal p,UUID moneda,String buscar,int offset,int limite,UUID id,boolean bloquear) {
        return jdbc.query("""
            SELECT id,internal_number FROM sales_invoice WHERE %s AND currency_id=:moneda AND status='CONFIRMED'
              AND (:todas OR branch_id IN (:sucursales))
              AND (CAST(:id AS uuid) IS NULL OR id=:id)
              AND (:buscar='' OR strpos(lower(internal_number),lower(:buscar))>0)
            ORDER BY invoice_date DESC,id LIMIT :limite OFFSET :offset
            """.formatted(AMBITO)+(bloquear?" FOR SHARE":""),alcance(p).addValue("moneda",moneda).addValue("buscar",buscar)
            .addValue("offset",offset).addValue("limite",limite).addValue("id",id),(r,n)->new Factura(uuid(r,"id"),r.getString("internal_number")));
    }

    public String siguiente(TenantPrincipal p) {
        long numero=jdbc.queryForObject("""
            INSERT INTO activo_baja_secuencia(tenant_id,empresa_id,ultimo) VALUES(:tenant,:empresa,1)
            ON CONFLICT(tenant_id,empresa_id) DO UPDATE SET ultimo=activo_baja_secuencia.ultimo+1 RETURNING ultimo
            """,alcance(p),Long.class);
        return String.format(Locale.ROOT,"BAJ-%06d",numero);
    }

    public void crear(TenantPrincipal p,UUID id,String codigo,Entrada e,Activo a) {
        jdbc.update("""
            INSERT INTO activo_baja(id,tenant_id,empresa_id,sucursal_id,activo_id,codigo,tipo,fecha,motivo,
                obtenido,moneda_id,referencia,factura_id,observacion,creado_por)
            VALUES(:id,:tenant,:empresa,:sucursal,:activo,:codigo,:tipo,:fecha,:motivo,
                :obtenido,:moneda,:referencia,:factura,:observacion,:usuario)
            """,datos(p,id,e,a).addValue("codigo",codigo).addValue("usuario",p.usuarioId()));
    }

    public void editar(TenantPrincipal p,UUID id,Entrada e,Activo a) {
        jdbc.update("""
            UPDATE activo_baja SET sucursal_id=:sucursal,activo_id=:activo,tipo=:tipo,fecha=:fecha,motivo=:motivo,
                obtenido=:obtenido,moneda_id=:moneda,referencia=:referencia,factura_id=:factura,observacion=:observacion,
                version=version+1 WHERE id=:id AND
            """+AMBITO,datos(p,id,e,a));
    }

    public void confirmar(TenantPrincipal p,Detalle d,Snapshot snapshot,UUID solicitud) {
        var a=snapshot.activo();
        var args=datos(p,d.id(),d.datos(),a).addValue("usuario",p.usuarioId()).addValue("solicitud",solicitud)
            .addValue("snapshot",JSON.writeValueAsString(snapshot)).addValue("costo",a.costo())
            .addValue("depreciacion",a.depreciacion()).addValue("libros",a.libros()).addValue("codigo",d.codigo());
        jdbc.update("""
            UPDATE activo_baja SET estado='CONFIRMADA',sucursal_id=:sucursal,snapshot=CAST(:snapshot AS jsonb),costo=:costo,
                depreciacion=:depreciacion,libros=:libros,confirmado_por=:usuario,confirmado_en=current_timestamp,
                solicitud_id=:solicitud,version=version+1 WHERE id=:id AND
            """+AMBITO,args);
        jdbc.update("""
            UPDATE activo_fijo SET estado='BAJA',condicion='FUERA_SERVICIO' WHERE id=:activo AND
            """+AMBITO,args);
        jdbc.update("""
            INSERT INTO activo_fijo_movimiento(tenant_id,empresa_id,activo_id,sucursal_id,tipo,fecha,descripcion,importe,baja_id)
            VALUES(:tenant,:empresa,:activo,:sucursal,'BAJA',:fecha,:codigo,:libros,:id)
            """,args);
    }

    public boolean periodoCerrado(TenantPrincipal p,LocalDate fecha) {
        return jdbc.query("SELECT estado FROM periodos_fiscales WHERE "+AMBITO
            +" AND fecha_inicial<=:fecha AND fecha_final>=:fecha FOR SHARE",alcance(p).addValue("fecha",fecha),
            (r,n)->r.getString(1)).stream().anyMatch(estado->!"ABIERTO".equals(estado));
    }

    private static Activo activo(ResultSet r)throws SQLException {
        return new Activo(uuid(r,"id"),uuid(r,"sucursal_id"),uuid(r,"moneda_id"),r.getString("moneda"),r.getString("codigo"),
            r.getString("nombre"),r.getString("categoria_nombre"),r.getString("marca"),r.getString("modelo"),r.getString("serie"),
            r.getString("estado"),r.getObject("fecha_adquisicion",LocalDate.class),r.getObject("inicio_depreciacion",LocalDate.class),
            r.getObject("ultima",LocalDate.class),r.getBigDecimal("costo"),r.getBigDecimal("depreciacion"),r.getBigDecimal("ajustes"),
            r.getBoolean("retirado"),r.getBoolean("posterior"),r.getBoolean("mantenimiento"),r.getLong("version"));
    }

    private static MapSqlParameterSource datos(TenantPrincipal p,UUID id,Entrada e,Activo a) {
        return alcance(p).addValue("id",id).addValue("sucursal",a.sucursal()).addValue("activo",a.id()).addValue("moneda",a.monedaId())
            .addValue("tipo",e.tipo()).addValue("fecha",e.fecha()).addValue("motivo",e.motivo()).addValue("obtenido",e.obtenido())
            .addValue("referencia",e.referencia()).addValue("factura",e.factura()).addValue("observacion",e.observacion());
    }

    private static MapSqlParameterSource alcance(TenantPrincipal p) {
        return new MapSqlParameterSource("tenant",p.tenantId()).addValue("empresa",p.empresaId()).addValue("todas",p.accesoTodasSucursales())
            .addValue("sucursales",p.sucursalIds().isEmpty()?List.of(new UUID(0,0)):p.sucursalIds());
    }

    private static UUID uuid(ResultSet r,String campo)throws SQLException { return r.getObject(campo,UUID.class); }
}
