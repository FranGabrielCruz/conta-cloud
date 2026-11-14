package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.dto.DepreciacionDto.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DepreciacionRepository {
    private static final String ALCANCE = "tenant_id=:tenant AND empresa_id=:empresa";
    private static final String SUCURSAL = " AND (:todas OR d.sucursal_id IN (:sucursales))";
    private static final String HISTORIAL = """
        FROM activo_depreciacion_proceso p JOIN activo_depreciacion_detalle d
          ON d.tenant_id=p.tenant_id AND d.empresa_id=p.empresa_id AND d.proceso_id=p.id
        WHERE p.tenant_id=:tenant AND p.empresa_id=:empresa
          AND (CAST(:desde AS date) IS NULL OR p.fecha>=:desde)
          AND (CAST(:hasta AS date) IS NULL OR p.fecha<=:hasta)
        """+SUCURSAL;
    private final NamedParameterJdbcTemplate jdbc;

    public DepreciacionRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }

    public void bloquearEmpresa(TenantPrincipal p) {
        jdbc.queryForObject("SELECT id FROM empresas WHERE tenant_id=:tenant AND id=:empresa FOR UPDATE",params(p),UUID.class);
    }

    public boolean periodoAbierto(TenantPrincipal p,YearMonth periodo,boolean bloquear) {
        return !jdbc.query("""
            SELECT id FROM periodos_fiscales WHERE %s AND estado='ABIERTO'
              AND fecha_inicial<=:inicio AND fecha_final>=:fin
            """.formatted(ALCANCE)+(bloquear?" FOR SHARE":""),fechas(p,periodo),(r,n)->r.getObject(1,UUID.class)).isEmpty();
    }

    public Optional<Proceso> ultimo(TenantPrincipal p) {
        return proceso(" ORDER BY periodo DESC LIMIT 1",params(p));
    }

    public Optional<Proceso> solicitud(TenantPrincipal p,UUID solicitud) {
        return proceso(" AND solicitud_id=:solicitud",params(p).addValue("solicitud",solicitud));
    }

    public Optional<Proceso> procesoVisible(TenantPrincipal p,UUID id) {
        return proceso("""
            AND id=:id AND EXISTS (SELECT 1 FROM activo_depreciacion_detalle d
              WHERE d.tenant_id=activo_depreciacion_proceso.tenant_id
              AND d.empresa_id=activo_depreciacion_proceso.empresa_id
              AND d.proceso_id=activo_depreciacion_proceso.id
            """+SUCURSAL+")",params(p).addValue("id",id));
    }

    private Optional<Proceso> proceso(String consulta,MapSqlParameterSource args) {
        return jdbc.query("SELECT * FROM activo_depreciacion_proceso WHERE "+ALCANCE+" "+consulta,args,
            (r,n)->new Proceso(r.getObject("id",UUID.class),r.getLong("numero"),
                YearMonth.from(r.getObject("periodo",LocalDate.class)),r.getObject("fecha",LocalDate.class),
                r.getObject("procesado_en",OffsetDateTime.class),r.getObject("usuario_id",UUID.class),
                r.getString("usuario_nombre"),r.getObject("solicitud_id",UUID.class),
                new Empresa(r.getString("empresa_nombre"),r.getString("rnc"),r.getString("direccion"),
                    r.getString("telefono"),r.getString("pais"),r.getString("logo")))).stream().findFirst();
    }

    public Page<Fila> historial(TenantPrincipal p,LocalDate desde,LocalDate hasta,Pageable pagina) {
        var args=params(p).addValue("desde",desde).addValue("hasta",hasta)
            .addValue("limite",pagina.getPageSize()).addValue("offset",pagina.getOffset());
        var filas=jdbc.query("""
            SELECT p.id,p.periodo,p.fecha,count(*) activos,d.moneda,sum(d.importe) importe
            """+HISTORIAL+" GROUP BY p.id,p.periodo,p.fecha,d.moneda ORDER BY p.periodo DESC,d.moneda LIMIT :limite OFFSET :offset",
            args,(r,n)->new Fila(r.getObject("id",UUID.class),YearMonth.from(r.getObject("periodo",LocalDate.class)),
                r.getObject("fecha",LocalDate.class),r.getLong("activos"),r.getString("moneda"),r.getBigDecimal("importe")));
        long cantidad=jdbc.queryForObject("SELECT count(DISTINCT (p.id,d.moneda)) "+HISTORIAL,args,Long.class);
        return new PageImpl<>(filas,pagina,cantidad);
    }

    public List<Linea> detalle(TenantPrincipal p,UUID id) {
        return jdbc.query("SELECT d.* FROM activo_depreciacion_detalle d WHERE "+ALCANCE+" AND proceso_id=:id"+SUCURSAL
            +" ORDER BY moneda,codigo,activo_id",params(p).addValue("id",id),(r,n)->new Linea(
            r.getObject("activo_id",UUID.class),r.getObject("sucursal_id",UUID.class),r.getString("codigo"),r.getString("nombre"),
            r.getString("moneda"),r.getBigDecimal("costo"),r.getBigDecimal("base"),r.getBigDecimal("anterior"),
            r.getBigDecimal("importe"),r.getBigDecimal("libros")));
    }

    public void bloquearActivos(TenantPrincipal p) {
        jdbc.query("""
            SELECT id FROM activo_fijo WHERE %s
            ORDER BY id FOR UPDATE
            """.formatted(ALCANCE),params(p),(r,n)->r.getObject(1,UUID.class));
    }

    public List<Candidato> candidatos(TenantPrincipal p,YearMonth periodo) {
        return jdbc.query("""
            SELECT a.id,a.sucursal_id,a.codigo,a.nombre,m.codigo_iso,a.costo,a.residual,a.vida_meses,a.metodo,a.inicio_depreciacion,
                   COALESCE(mov.anterior,0) anterior,COALESCE(mov.ajustes,0) ajustes,COALESCE(mov.posterior,false) posterior,
                   COALESCE(mov.anterior_actual,0) anterior_actual
            FROM activo_fijo a JOIN monedas m ON m.empresa_id=a.empresa_id AND m.id=a.moneda_id
            LEFT JOIN LATERAL (
              SELECT sum(v.importe) FILTER (WHERE v.tipo='DEPRECIACION' AND v.fecha<:inicio) anterior,
                sum(v.importe) FILTER (WHERE v.tipo='DEPRECIACION') anterior_actual,
                sum(v.importe) FILTER (WHERE v.tipo='AJUSTE' AND v.fecha<=:fin) ajustes,
                bool_or((v.tipo='DEPRECIACION' AND v.fecha>=:inicio) OR (v.tipo='AJUSTE' AND v.fecha>:fin)) posterior,
                bool_or(v.tipo='BAJA') baja
              FROM activo_fijo_movimiento v
              WHERE v.tenant_id=a.tenant_id AND v.empresa_id=a.empresa_id AND v.activo_id=a.id
            ) mov ON true
            WHERE a.tenant_id=:tenant AND a.empresa_id=:empresa AND a.estado='ACTIVO' AND a.depreciable
              AND (:todas OR a.sucursal_id IN (:sucursales)) AND NOT COALESCE(mov.baja,false)
              AND (a.inicio_depreciacion IS NULL OR a.inicio_depreciacion<=:fin)
            ORDER BY m.codigo_iso,a.codigo,a.id
            """,fechas(p,periodo),(r,n)->new Candidato(r.getObject("id",UUID.class),r.getObject("sucursal_id",UUID.class),
            r.getString("codigo"),r.getString("nombre"),r.getString("codigo_iso"),r.getBigDecimal("costo"),
            r.getBigDecimal("residual"),r.getObject("vida_meses",Integer.class),r.getString("metodo"),
            r.getObject("inicio_depreciacion",LocalDate.class),r.getBigDecimal("anterior"),r.getBigDecimal("ajustes"),
            r.getBoolean("posterior"),r.getBigDecimal("anterior_actual")));
    }

    public Indicadores indicadores(TenantPrincipal p,YearMonth periodo) {
        var args=fechas(p,periodo);
        var ultimo=jdbc.queryForObject("""
            SELECT max(p.periodo) FROM activo_depreciacion_proceso p
            WHERE p.tenant_id=:tenant AND p.empresa_id=:empresa AND EXISTS (
              SELECT 1 FROM activo_depreciacion_detalle d WHERE d.tenant_id=p.tenant_id
                AND d.empresa_id=p.empresa_id AND d.proceso_id=p.id
            """+SUCURSAL+")",args,LocalDate.class);
        var acumulados=jdbc.query("""
            SELECT m.codigo_iso,sum(v.importe) importe
            FROM activo_fijo a JOIN monedas m ON m.empresa_id=a.empresa_id AND m.id=a.moneda_id
            JOIN activo_fijo_movimiento v ON v.tenant_id=a.tenant_id AND v.empresa_id=a.empresa_id AND v.activo_id=a.id
            WHERE a.tenant_id=:tenant AND a.empresa_id=:empresa AND v.tipo='DEPRECIACION'
              AND (:todas OR a.sucursal_id IN (:sucursales))
              AND a.estado<>'BAJA' AND NOT EXISTS(SELECT 1 FROM activo_fijo_movimiento b
                WHERE b.tenant_id=a.tenant_id AND b.empresa_id=a.empresa_id AND b.activo_id=a.id AND b.tipo='BAJA')
            GROUP BY m.codigo_iso ORDER BY m.codigo_iso
            """,args,(r,n)->new Saldo(r.getString(1),r.getBigDecimal(2)));
        return new Indicadores(ultimo==null?null:YearMonth.from(ultimo),0,acumulados);
    }

    public void guardar(TenantPrincipal p,Proceso proceso,List<Linea> lineas) {
        var e=proceso.empresa();
        var cabecera=params(p).addValue("id",proceso.id()).addValue("numero",proceso.numero())
            .addValue("periodo",proceso.periodo().atDay(1)).addValue("fecha",proceso.fecha())
            .addValue("usuario",proceso.usuario()).addValue("nombreUsuario",proceso.nombreUsuario())
            .addValue("solicitud",proceso.solicitud()).addValue("nombreEmpresa",e.nombre())
            .addValue("rnc",e.rnc()).addValue("direccion",e.direccion()).addValue("telefono",e.telefono())
            .addValue("pais",e.pais()).addValue("logo",e.logo());
        jdbc.update("""
            INSERT INTO activo_depreciacion_proceso(id,tenant_id,empresa_id,numero,periodo,fecha,usuario_id,
              usuario_nombre,solicitud_id,empresa_nombre,rnc,direccion,telefono,pais,logo)
            VALUES(:id,:tenant,:empresa,:numero,:periodo,:fecha,:usuario,:nombreUsuario,:solicitud,:nombreEmpresa,
              :rnc,:direccion,:telefono,:pais,:logo)
            """,cabecera);
        var lote=lineas.stream().map(l->params(p).addValue("id",proceso.id()).addValue("activo",l.activo())
            .addValue("sucursal",l.sucursal()).addValue("codigo",l.codigo()).addValue("nombre",l.nombre())
            .addValue("moneda",l.moneda()).addValue("costo",l.costo()).addValue("base",l.base())
            .addValue("anterior",l.anterior()).addValue("importe",l.importe()).addValue("libros",l.libros())
            .addValue("fecha",proceso.fecha()).addValue("descripcion",proceso.codigo()+" · "+proceso.periodo()))
            .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate("""
            INSERT INTO activo_depreciacion_detalle(tenant_id,empresa_id,proceso_id,activo_id,sucursal_id,
                codigo,nombre,moneda,costo,base,anterior,importe,libros)
            VALUES(:tenant,:empresa,:id,:activo,:sucursal,:codigo,:nombre,:moneda,:costo,:base,:anterior,:importe,:libros)
            """,lote);
        jdbc.batchUpdate("""
            INSERT INTO activo_fijo_movimiento(tenant_id,empresa_id,activo_id,sucursal_id,tipo,fecha,descripcion,importe,depreciacion_proceso_id)
            VALUES(:tenant,:empresa,:activo,:sucursal,'DEPRECIACION',:fecha,:descripcion,:importe,:id)
            """,lote);
    }

    private static MapSqlParameterSource params(TenantPrincipal p) {
        return new MapSqlParameterSource("tenant",p.tenantId()).addValue("empresa",p.empresaId())
            .addValue("todas",p.accesoTodasSucursales())
            .addValue("sucursales",p.sucursalIds().isEmpty()?List.of(new UUID(0,0)):p.sucursalIds());
    }

    private static MapSqlParameterSource fechas(TenantPrincipal p,YearMonth periodo) {
        return params(p).addValue("inicio",periodo.atDay(1)).addValue("fin",periodo.atEndOfMonth());
    }
}
