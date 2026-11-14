package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.dto.MantenimientoDto.*;
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
public class MantenimientoRepository {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private static final String AMBITO="tenant_id=:tenant AND empresa_id=:empresa";
    private static final String SUCURSAL=" AND (:todas OR m.sucursal_id IN (:sucursales))";
    private static final String AMBITO_ACTIVO="a.tenant_id=:tenant AND a.empresa_id=:empresa AND (:todas OR a.sucursal_id IN (:sucursales))";
    private static final String BAJA="""
        (a.estado='BAJA' OR EXISTS(SELECT 1 FROM activo_fijo_movimiento b WHERE b.tenant_id=a.tenant_id
            AND b.empresa_id=a.empresa_id AND b.activo_id=a.id AND b.tipo='BAJA'))
        """.trim();
    private static final String ORIGEN="""
        FROM activo_mantenimiento m
        JOIN activo_fijo a ON a.tenant_id=m.tenant_id AND a.empresa_id=m.empresa_id AND a.id=m.activo_id
        JOIN activo_mantenimiento_tipo t ON t.codigo=m.tipo
        JOIN monedas c ON c.empresa_id=m.empresa_id AND c.id=m.moneda_id
        LEFT JOIN supplier p ON p.tenant_id=m.tenant_id AND p.empresa_id=m.empresa_id AND p.id=m.proveedor_id
        LEFT JOIN usuarios u ON u.tenant_id=m.tenant_id AND u.id=m.responsable_id
        WHERE m.tenant_id=:tenant AND m.empresa_id=:empresa
        """;
    private static final String VENCIDO="(m.estado IN ('PROGRAMADO','EN_PROCESO') AND m.programada<:hoy)";
    private final NamedParameterJdbcTemplate jdbc;

    public MantenimientoRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }

    public Page<Fila> listar(TenantPrincipal p,Filtro filtro,LocalDate hoy,Pageable pagina) {
        var args=alcance(p).addValue("hoy",hoy).addValue("desde",filtro.desde()).addValue("hasta",filtro.hasta())
            .addValue("tipo",filtro.tipo()).addValue("estado",filtro.estado()==null?null:filtro.estado().name())
            .addValue("vencido",filtro.vencido()).addValue("buscar",filtro.buscar())
            .addValue("offset",pagina.getOffset()).addValue("limite",pagina.getPageSize());
        String origen=ORIGEN+SUCURSAL+"""
             AND (CAST(:desde AS date) IS NULL OR m.programada>=:desde)
             AND (CAST(:hasta AS date) IS NULL OR m.programada<=:hasta)
             AND (CAST(:tipo AS varchar) IS NULL OR m.tipo=:tipo)
             AND (CAST(:estado AS varchar) IS NULL OR m.estado=:estado)
             AND (:buscar='' OR strpos(lower(concat_ws(' ',m.codigo,a.codigo,COALESCE(m.snapshot->>'activoNombre',a.nombre),
               COALESCE(m.snapshot->>'proveedor',p.trade_name))),lower(:buscar))>0)
            """+" AND (NOT :vencido OR "+VENCIDO+")";
        var filas=jdbc.query(filaSql()+origen+" ORDER BY m.programada DESC,m.codigo DESC LIMIT :limite OFFSET :offset",
            args,(r,n)->fila(r));
        long total=jdbc.queryForObject("SELECT count(*) "+origen,args,Long.class);
        return new PageImpl<>(filas,pagina,total);
    }

    public List<Fila> historialActivo(TenantPrincipal p,UUID activo,LocalDate hoy) {
        return jdbc.query(filaSql()+ORIGEN+SUCURSAL+" AND m.activo_id=:activo ORDER BY m.programada DESC,m.codigo DESC",
            alcance(p).addValue("activo",activo).addValue("hoy",hoy),(r,n)->fila(r));
    }

    private static String filaSql() {
        return """
            SELECT m.id,m.codigo,a.codigo activo_codigo,COALESCE(m.snapshot->>'activoNombre',a.nombre) activo_nombre,
               COALESCE(m.snapshot->>'tipo',t.nombre) tipo_nombre,COALESCE(m.realizada,m.programada) fecha,COALESCE(m.real,m.estimado) costo,
               COALESCE(m.snapshot->>'moneda',c.codigo_iso) moneda,m.estado,COALESCE(m.trabajo,m.descripcion) descripcion,
            """+VENCIDO+" vencido ";
    }

    private static Fila fila(ResultSet r)throws SQLException {
        return new Fila(uuid(r,"id"),r.getString("codigo"),r.getString("activo_codigo"),r.getString("activo_nombre"),
            r.getString("tipo_nombre"),r.getObject("fecha",LocalDate.class),r.getBigDecimal("costo"),r.getString("moneda"),
            Estado.valueOf(r.getString("estado")),r.getBoolean("vencido"),r.getString("descripcion"));
    }

    public Indicadores indicadores(TenantPrincipal p,LocalDate hoy,int dias) {
        var args=alcance(p).addValue("hoy",hoy).addValue("ventana",hoy.plusDays(dias))
            .addValue("inicioMes",hoy.withDayOfMonth(1)).addValue("finMes",hoy.withDayOfMonth(1).plusMonths(1));
        var cifras=jdbc.queryForObject("""
            SELECT count(*) FILTER(WHERE m.estado='PROGRAMADO'),
                count(*) FILTER(WHERE m.estado='PROGRAMADO' AND m.programada BETWEEN :hoy AND :ventana),
                count(*) FILTER(WHERE %s)
            """.formatted(VENCIDO)+ORIGEN+SUCURSAL,args,(r,n)->new Indicadores(r.getLong(1),r.getLong(2),r.getLong(3),List.of()));
        var costos=jdbc.query("""
            SELECT COALESCE(m.snapshot->>'moneda',c.codigo_iso) moneda,sum(m.real)
            """+ORIGEN+SUCURSAL+" AND m.estado='COMPLETADO' AND m.realizada>=:inicioMes AND m.realizada<:finMes GROUP BY 1 ORDER BY 1",
            args,(r,n)->new Saldo(r.getString(1),r.getBigDecimal(2)));
        return new Indicadores(cifras.programados(),cifras.proximos(),cifras.vencidos(),costos);
    }

    public List<Tipo> tipos() {
        return jdbc.query("SELECT codigo,nombre FROM activo_mantenimiento_tipo ORDER BY nombre",(r,n)->new Tipo(r.getString(1),r.getString(2)));
    }

    public List<Activo> buscarActivos(TenantPrincipal p,String buscar,int offset,int limite) {
        return jdbc.query(columnasActivo()+" FROM activo_fijo a WHERE "+AMBITO_ACTIVO+" AND NOT "+BAJA+"""
             AND (:buscar='' OR strpos(lower(concat_ws(' ',a.codigo,a.nombre,a.marca,a.modelo,a.serie)),lower(:buscar))>0)
             ORDER BY a.codigo LIMIT :limite OFFSET :offset
            """,alcance(p).addValue("buscar",buscar).addValue("offset",offset).addValue("limite",limite),(r,n)->activo(r));
    }

    public Optional<Activo> activo(TenantPrincipal p,UUID id,boolean bloquear) {
        var args=alcance(p).addValue("id",id);
        if (bloquear) jdbc.query("SELECT a.id FROM activo_fijo a WHERE "+AMBITO_ACTIVO+" AND a.id=:id FOR UPDATE OF a",
            args,(r,n)->r.getObject(1,UUID.class));
        return jdbc.query(columnasActivo()+" FROM activo_fijo a WHERE "+AMBITO_ACTIVO+" AND a.id=:id",
            args,(r,n)->activo(r)).stream().findFirst();
    }

    public Optional<Detalle> detalle(TenantPrincipal p,UUID id,boolean bloquear) {
        if (bloquear) {
            var bloqueo=jdbc.query("SELECT m.id FROM activo_mantenimiento m WHERE "+AMBITO+SUCURSAL+" AND m.id=:id FOR UPDATE",
                alcance(p).addValue("id",id),(r,n)->r.getObject(1,UUID.class));
            if (bloqueo.isEmpty()) return Optional.empty();
        }
        return jdbc.query("""
            SELECT m.*,a.codigo activo_codigo,a.nombre activo_nombre,a.marca,a.modelo,a.serie,a.condicion,
                a.sucursal_id activo_sucursal,a.estado estado_activo,%s baja,t.nombre tipo_nombre,c.codigo_iso moneda,
                NULLIF(concat_ws(' ',u.nombre,NULLIF(u.apellido,'')),'') responsable,p.trade_name proveedor
            """.formatted(BAJA)+ORIGEN+SUCURSAL+" AND m.id=:id",
            alcance(p).addValue("id",id),(r,n)->detalle(r)).stream().findFirst();
    }

    public boolean repetida(TenantPrincipal p,UUID id,UUID solicitud,Estado estado) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM activo_mantenimiento m WHERE %s AND m.id=:id
                AND m.solicitud_id=:solicitud AND m.estado=:estado
                AND COALESCE(m.cerrado_por,m.iniciado_por)=:usuario%s)
            """.formatted(AMBITO,SUCURSAL),alcance(p).addValue("id",id).addValue("solicitud",solicitud)
            .addValue("estado",estado.name()).addValue("usuario",p.usuarioId()),Boolean.class));
    }

    private static Detalle detalle(ResultSet r)throws SQLException {
        var a=new Activo(uuid(r,"activo_id"),uuid(r,"activo_sucursal"),r.getString("activo_codigo"),r.getString("activo_nombre"),
            r.getString("marca"),r.getString("modelo"),r.getString("serie"),r.getString("condicion"),r.getBoolean("baja"),r.getString("estado_activo"));
        var datos=new Entrada(a.id(),r.getString("tipo"),Prioridad.valueOf(r.getString("prioridad")),
            r.getObject("programada",LocalDate.class),r.getObject("proxima",LocalDate.class),
            uuid(r,"responsable_id"),uuid(r,"proveedor_id"),uuid(r,"moneda_id"),r.getBigDecimal("estimado"),
            r.getString("referencia"),r.getString("descripcion"),r.getString("observacion"));
        var snapshot=r.getString("snapshot");
        return new Detalle(uuid(r,"id"),uuid(r,"sucursal_id"),r.getString("codigo"),r.getLong("version"),
            Estado.valueOf(r.getString("estado")),datos,a,r.getString("tipo_nombre"),r.getString("moneda"),
            r.getString("responsable"),r.getString("proveedor"),r.getBoolean("fuera_servicio"),r.getString("condicion_anterior"),
            r.getObject("inicio",OffsetDateTime.class),r.getObject("cierre",OffsetDateTime.class),
            r.getObject("realizada",LocalDate.class),r.getBigDecimal("real"),r.getString("trabajo"),r.getString("motivo"),
            snapshot==null?null:JSON.readValue(snapshot,Snapshot.class));
    }

    private static String columnasActivo() {
        return "SELECT a.id,a.sucursal_id,a.codigo,a.nombre,a.marca,a.modelo,a.serie,a.condicion,a.estado estado_activo,"+BAJA+" baja ";
    }

    private static Activo activo(ResultSet r)throws SQLException {
        return new Activo(uuid(r,"id"),uuid(r,"sucursal_id"),r.getString("codigo"),r.getString("nombre"),r.getString("marca"),
            r.getString("modelo"),r.getString("serie"),r.getString("condicion"),r.getBoolean("baja"),r.getString("estado_activo"));
    }

    public String siguienteCodigo(TenantPrincipal p) {
        long numero=jdbc.queryForObject("""
            INSERT INTO activo_mantenimiento_secuencia(tenant_id,empresa_id,ultimo) VALUES(:tenant,:empresa,1)
            ON CONFLICT(tenant_id,empresa_id) DO UPDATE SET ultimo=activo_mantenimiento_secuencia.ultimo+1 RETURNING ultimo
            """,alcance(p),Long.class);
        return String.format(Locale.ROOT,"MAN-%06d",numero);
    }

    public void crear(TenantPrincipal p,UUID id,UUID sucursal,String codigo,Entrada e) {
        jdbc.update("""
            INSERT INTO activo_mantenimiento(id,tenant_id,empresa_id,sucursal_id,activo_id,codigo,tipo,prioridad,
              programada,proxima,responsable_id,proveedor_id,moneda_id,estimado,referencia,descripcion,observacion,creado_por)
            VALUES(:id,:tenant,:empresa,:sucursal,:activo,:codigo,:tipo,:prioridad,:programada,:proxima,:responsable,
              :proveedor,:moneda,:estimado,:referencia,:descripcion,:observacion,:usuario)
            """,datos(p,id,e).addValue("sucursal",sucursal).addValue("codigo",codigo).addValue("usuario",p.usuarioId()));
    }

    public void editar(TenantPrincipal p,UUID id,Entrada e) {
        jdbc.update("""
            UPDATE activo_mantenimiento SET tipo=:tipo,prioridad=:prioridad,programada=:programada,proxima=:proxima,
              responsable_id=:responsable,proveedor_id=:proveedor,moneda_id=:moneda,estimado=:estimado,referencia=:referencia,
              descripcion=:descripcion,observacion=:observacion,version=version+1
            WHERE id=:id AND
            """+AMBITO,datos(p,id,e));
    }

    public void iniciar(TenantPrincipal p,Detalle d,String condicionAnterior,boolean fuera,UUID solicitud) {
        jdbc.update("""
            UPDATE activo_mantenimiento SET estado='EN_PROCESO',fuera_servicio=:fuera,condicion_anterior=:anterior,
              inicio=current_timestamp,iniciado_por=:usuario,solicitud_id=:solicitud,version=version+1 WHERE id=:id AND
            """+AMBITO,transicion(p,d,solicitud).addValue("fuera",fuera).addValue("anterior",fuera?condicionAnterior:null));
    }

    public void completar(TenantPrincipal p,Detalle d,Completar c,Snapshot snapshot,UUID solicitud) {
        jdbc.update("""
            UPDATE activo_mantenimiento SET estado='COMPLETADO',realizada=:fecha,real=:costo,proveedor_id=:proveedor,
              trabajo=:trabajo,proxima=:proxima,observacion=:observacion,snapshot=CAST(:snapshot AS jsonb),
              cierre=current_timestamp,cerrado_por=:usuario,solicitud_id=:solicitud,version=version+1 WHERE id=:id AND
            """+AMBITO,transicion(p,d,solicitud).addValue("fecha",c.realizada()).addValue("costo",c.costo())
            .addValue("proveedor",c.proveedor()).addValue("trabajo",c.trabajo()).addValue("proxima",c.proxima())
            .addValue("observacion",c.observacion()).addValue("snapshot",JSON.writeValueAsString(snapshot)));
    }

    public void cancelar(TenantPrincipal p,Detalle d,String motivo,UUID solicitud) {
        jdbc.update("""
            UPDATE activo_mantenimiento SET estado='CANCELADO',motivo=:motivo,cierre=current_timestamp,
              cerrado_por=:usuario,solicitud_id=:solicitud,version=version+1 WHERE id=:id AND
            """+AMBITO,transicion(p,d,solicitud).addValue("motivo",motivo));
    }

    public void condicion(TenantPrincipal p,UUID id,String condicion,String anterior) {
        jdbc.update("""
            UPDATE activo_fijo SET condicion=:condicion,version=version+1
            WHERE id=:id AND condicion IS NOT DISTINCT FROM CAST(:anterior AS varchar) AND
            """+AMBITO,alcance(p).addValue("id",id).addValue("condicion",condicion).addValue("anterior",anterior));
    }

    private static MapSqlParameterSource transicion(TenantPrincipal p,Detalle d,UUID solicitud) {
        return alcance(p).addValue("id",d.id()).addValue("usuario",p.usuarioId()).addValue("solicitud",solicitud);
    }

    private static MapSqlParameterSource datos(TenantPrincipal p,UUID id,Entrada e) {
        return alcance(p).addValue("id",id).addValue("activo",e.activo()).addValue("tipo",e.tipo()).addValue("prioridad",e.prioridad().name())
            .addValue("programada",e.programada()).addValue("proxima",e.proxima()).addValue("responsable",e.responsable())
            .addValue("proveedor",e.proveedor()).addValue("moneda",e.moneda()).addValue("estimado",e.estimado())
            .addValue("referencia",e.referencia()).addValue("descripcion",e.descripcion()).addValue("observacion",e.observacion());
    }

    private static MapSqlParameterSource alcance(TenantPrincipal p) {
        return new MapSqlParameterSource("tenant",p.tenantId()).addValue("empresa",p.empresaId())
            .addValue("todas",p.accesoTodasSucursales())
            .addValue("sucursales",p.sucursalIds().isEmpty()?List.of(new UUID(0,0)):p.sucursalIds());
    }

    private static UUID uuid(ResultSet r,String campo)throws SQLException { return r.getObject(campo,UUID.class); }
}
