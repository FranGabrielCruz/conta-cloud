package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.dto.CierreContableDto.*;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.Movimiento;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class CierreContableRepository {
    private static final String AMBITO="p.tenant_id=:tenant AND p.empresa_id=:empresa";
    private static final String FILTROS="""
         AND (CAST(:id AS uuid) IS NULL OR p.id=:id)
         AND (CAST(:estado AS text) IS NULL OR p.estado=:estado)
         AND (:buscar='' OR strpos(lower(concat_ws(' ',p.nombre,p.anio,p.mes)),lower(:buscar))>0)
        """;
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private final NamedParameterJdbcTemplate jdbc;

    public CierreContableRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }

    public Lista listar(TenantPrincipal p,Filtro f,long offset,int limite) {
        var args=ambito(p).addValue("id",f.periodo()).addValue("estado",f.estado()==null?null:f.estado().name())
            .addValue("buscar",f.buscar()).addValue("offset",offset).addValue("limite",limite);
        long total=jdbc.queryForObject("SELECT count(*) FROM periodos_fiscales p WHERE "+AMBITO+FILTROS,args,Long.class);
        var periodos=jdbc.query("SELECT p.* FROM periodos_fiscales p WHERE "+AMBITO+FILTROS
            +" ORDER BY fecha_inicial DESC,id LIMIT :limite OFFSET :offset",args,CierreContableRepository::periodo);
        return new Lista(periodos,total);
    }

    public Optional<Periodo> periodo(TenantPrincipal p,UUID id) {
        return jdbc.query("SELECT p.* FROM periodos_fiscales p WHERE "+AMBITO+" AND id=:id",
            ambito(p).addValue("id",id),CierreContableRepository::periodo).stream().findFirst();
    }

    public void bloquearPeriodo(TenantPrincipal p,UUID id) {
        jdbc.queryForObject("SELECT id FROM periodos_fiscales p WHERE "+AMBITO+" AND id=:id FOR UPDATE",
            ambito(p).addValue("id",id),UUID.class);
    }

    public PeriodosAnteriores anteriores(TenantPrincipal p,LocalDate inicioFiscal,LocalDate inicioPeriodo) {
        return jdbc.queryForObject("""
            SELECT count(*),count(*) FILTER (WHERE p.estado<>'CERRADO')
            FROM periodos_fiscales p WHERE
            """+AMBITO+" AND p.fecha_final>=:desde AND p.fecha_final<:hasta",
            fechas(p,inicioFiscal,inicioPeriodo),(r,n)->new PeriodosAnteriores(r.getLong(1),r.getLong(2)));
    }

    public int mesInicio(TenantPrincipal p) {
        return jdbc.query("SELECT mes_inicio_fiscal FROM configuracion_contable p WHERE "+AMBITO,ambito(p),
            (r,n)->r.getInt(1)).stream().findFirst().orElse(1);
    }

    public record Cuadre(long borradores,long descuadrados,boolean balanza,boolean monedaValida) {}

    public Cuadre cuadre(TenantPrincipal p,LocalDate desde,LocalDate hasta,String moneda) {
        return jdbc.queryForObject("""
            WITH asientos AS (
                SELECT a.id,a.estado,a.fecha,a.moneda,count(l.orden) AS lineas,coalesce(sum(l.debito-l.credito),0) AS diferencia
                FROM asiento_contable a LEFT JOIN asiento_contable_linea l
                  ON (l.tenant_id,l.empresa_id,l.asiento_id)=(a.tenant_id,a.empresa_id,a.id)
                WHERE a.tenant_id=:tenant AND a.empresa_id=:empresa AND a.fecha<=:hasta
                  AND (a.estado<>'BORRADOR' OR a.fecha>=:desde)
                GROUP BY a.id
            ), balanza AS (
                SELECT moneda,sum(diferencia) FILTER (WHERE fecha<:desde) AS inicial,
                       sum(diferencia) FILTER (WHERE fecha>=:desde) AS periodo,sum(diferencia) AS final
                FROM asientos WHERE estado<>'BORRADOR' GROUP BY moneda
            )
            SELECT count(*) FILTER (WHERE estado='BORRADOR') AS borradores,
                   count(*) FILTER (WHERE estado<>'BORRADOR' AND fecha>=:desde AND (lineas<2 OR diferencia<>0)) AS descuadrados,
                   NOT EXISTS (SELECT 1 FROM balanza WHERE inicial<>0 OR periodo<>0 OR final<>0) AS cuadra,
                   NOT EXISTS (SELECT 1 FROM asientos WHERE estado<>'BORRADOR' AND fecha>=:desde
                       AND moneda<>:moneda) AS moneda_valida
            FROM asientos
            """,fechas(p,desde,hasta).addValue("moneda",moneda),
            (r,n)->new Cuadre(r.getLong(1),r.getLong(2),r.getBoolean(3),r.getBoolean(4)));
    }

    private static final String TEMPORALES="""
        FROM asiento_movimiento_contable m
        JOIN cuenta_contable c ON (c.tenant_id,c.empresa_id,c.id)=(m.tenant_id,m.empresa_id,m.cuenta_id)
        WHERE m.tenant_id=:tenant AND m.empresa_id=:empresa AND m.fecha BETWEEN :desde AND :hasta
          AND c.clasificacion IN ('INGRESOS','COSTOS','GASTOS')
        """;

    public Resultado resultado(TenantPrincipal p,LocalDate desde,LocalDate hasta,String moneda) {
        return jdbc.queryForObject("""
            SELECT coalesce(sum(m.credito-m.debito) FILTER (WHERE c.clasificacion='INGRESOS'),0),
                   coalesce(sum(m.debito-m.credito) FILTER (WHERE c.clasificacion='COSTOS'),0),
                   coalesce(sum(m.debito-m.credito) FILTER (WHERE c.clasificacion='GASTOS'),0)
            """+TEMPORALES+" AND m.moneda=:moneda",fechas(p,desde,hasta).addValue("moneda",moneda),
            (r,n)->new Resultado(r.getBigDecimal(1),r.getBigDecimal(2),r.getBigDecimal(3)));
    }

    public List<Movimiento> cancelaciones(TenantPrincipal p,LocalDate desde,LocalDate hasta,String moneda) {
        return jdbc.query("SELECT m.cuenta_id,sum(m.credito-m.debito) AS neto "+TEMPORALES
            +" AND m.moneda=:moneda GROUP BY m.cuenta_id HAVING sum(m.credito-m.debito)<>0 ORDER BY m.cuenta_id",
            fechas(p,desde,hasta).addValue("moneda",moneda),(r,n)->{
                var neto=r.getBigDecimal("neto");
                return new Movimiento(r.getObject("cuenta_id",UUID.class),"Cierre de cuenta de resultados",
                    neto.max(BigDecimal.ZERO),neto.negate().max(BigDecimal.ZERO));
            });
    }

    private static final String ELEGIBLE="p.activo AND p.tipo='MOVIMIENTO' AND p.clasificacion='PATRIMONIO'"
        +" AND p.permite_resultado_ejercicio AND p.uso_centro_costo<>'OBLIGATORIO'";

    public record CuentaConfigurada(Cuenta cuenta,boolean valida) {}

    public Optional<CuentaConfigurada> cuentaResultado(TenantPrincipal p) {
        return jdbc.query("""
            SELECT p.id,p.codigo,p.nombre, (%s) AS valida FROM cuenta_contable_clave_empresa k
            JOIN cuenta_contable p ON (p.tenant_id,p.empresa_id,p.id)=(k.tenant_id,k.empresa_id,k.cuenta_id)
            WHERE k.clave='FISCAL_RESULT_DEFAULT' AND
            """.formatted(ELEGIBLE)+AMBITO,ambito(p),(r,n)->new CuentaConfigurada(cuenta(r,n),r.getBoolean("valida"))).stream().findFirst();
    }

    public List<Cuenta> buscarResultado(TenantPrincipal p,String buscar,int offset,int limite) {
        return jdbc.query("SELECT p.id,p.codigo,p.nombre FROM cuenta_contable p WHERE "+AMBITO+" AND "+ELEGIBLE+"""
             AND (:buscar='' OR strpos(lower(p.codigo || ' ' || p.nombre),lower(:buscar))>0)
             ORDER BY p.codigo LIMIT :limite OFFSET :offset
            """,ambito(p).addValue("buscar",buscar).addValue("offset",offset).addValue("limite",limite),CierreContableRepository::cuenta);
    }

    public boolean configurarCuenta(TenantPrincipal p,UUID id) {
        jdbc.update("UPDATE configuracion_contable SET version=version+1 WHERE tenant_id=:tenant AND empresa_id=:empresa",ambito(p));
        return jdbc.update("""
            INSERT INTO cuenta_contable_clave_empresa(tenant_id,empresa_id,clave,cuenta_id)
            SELECT p.tenant_id,p.empresa_id,'FISCAL_RESULT_DEFAULT',p.id FROM cuenta_contable p
            WHERE p.id=:id AND
            """+ELEGIBLE+" AND "+AMBITO+" ON CONFLICT(tenant_id,empresa_id,clave) DO UPDATE SET cuenta_id=EXCLUDED.cuenta_id",
            ambito(p).addValue("id",id))==1;
    }

    private static final String HISTORIA="""
        SELECT p.*,r.motivo,r.usuario,r.fecha AS reapertura_fecha,r.reversa_id,
               a.numero AS asiento_numero,b.numero AS reversa_numero
        FROM cierre_contable p LEFT JOIN reapertura_contable r
            ON (r.tenant_id,r.empresa_id,r.cierre_id)=(p.tenant_id,p.empresa_id,p.id)
        LEFT JOIN asiento_contable a ON (a.tenant_id,a.empresa_id,a.id)=(p.tenant_id,p.empresa_id,p.asiento_id)
        LEFT JOIN asiento_contable b ON (b.tenant_id,b.empresa_id,b.id)=(r.tenant_id,r.empresa_id,r.reversa_id)
        WHERE
        """;

    public List<Cierre> historial(TenantPrincipal p,UUID periodo) {
        return jdbc.query(HISTORIA+AMBITO+" AND p.periodo_id=:id ORDER BY p.activo DESC,p.cerrado_en DESC,p.id",
            ambito(p).addValue("id",periodo),CierreContableRepository::cierre);
    }

    public Optional<Cierre> cierre(TenantPrincipal p,UUID id) {
        return jdbc.query(HISTORIA+AMBITO+" AND p.id=:id",ambito(p).addValue("id",id),
            CierreContableRepository::cierre).stream().findFirst();
    }

    public Optional<UUID> solicitud(TenantPrincipal p,UUID clave,boolean reapertura) {
        var tabla=reapertura?"reapertura_contable":"cierre_contable";
        var id=reapertura?"cierre_id":"id";
        return jdbc.query("SELECT p."+id+" FROM "+tabla+" p WHERE "+AMBITO+" AND solicitud=:clave",
            ambito(p).addValue("clave",clave),(r,n)->r.getObject(1,UUID.class)).stream().findFirst();
    }

    public long posterioresCerrados(TenantPrincipal p,LocalDate fecha) {
        return jdbc.queryForObject("SELECT count(*) FROM periodos_fiscales p WHERE "+AMBITO+" AND fecha_inicial>:fecha AND estado<>'ABIERTO'",
            ambito(p).addValue("fecha",fecha),Long.class);
    }

    public void registrar(TenantPrincipal p,UUID id,UUID periodo,UUID clave,UUID asiento,Resumen resumen) {
        jdbc.update("""
            INSERT INTO cierre_contable(id,tenant_id,empresa_id,periodo_id,solicitud,asiento_id,resumen,cerrado_por)
            VALUES(:id,:tenant,:empresa,:periodo,:clave,:asiento,CAST(:resumen AS jsonb),:usuario)
            """,ambito(p).addValue("id",id).addValue("periodo",periodo).addValue("clave",clave)
            .addValue("asiento",asiento).addValue("resumen",JSON.writeValueAsString(resumen)).addValue("usuario",p.usuarioId()));
    }

    public Optional<String> usuarioCierreAnterior(TenantPrincipal p,UUID periodo) {
        return jdbc.query("""
            SELECT u.nombre FROM periodos_fiscales p
            JOIN usuarios u ON (u.empresa_id,u.id)=(p.empresa_id,p.usuario_cierre_id)
            WHERE p.estado='CERRADO' AND p.fecha_cierre IS NOT NULL AND p.id=:id AND
            """+AMBITO,ambito(p).addValue("id",periodo),(r,n)->r.getString(1)).stream().findFirst();
    }

    public void registrarCierreAnterior(TenantPrincipal p,UUID id,UUID periodo,Resumen resumen) {
        jdbc.update("""
            INSERT INTO cierre_contable(id,tenant_id,empresa_id,periodo_id,solicitud,cerrado_en,cerrado_por,resumen)
            SELECT :id,p.tenant_id,p.empresa_id,p.id,:id,p.fecha_cierre,p.usuario_cierre_id,CAST(:resumen AS jsonb)
            FROM periodos_fiscales p WHERE p.id=:periodo AND
            """+AMBITO,ambito(p).addValue("id",id).addValue("periodo",periodo)
            .addValue("resumen",JSON.writeValueAsString(resumen)));
    }

    public void estado(TenantPrincipal p,UUID id,boolean cerrado) {
        String campos=cerrado?"estado='CERRADO',fecha_cierre=current_timestamp,usuario_cierre_id=:usuario"
            :"estado='ABIERTO',fecha_reapertura=current_timestamp,usuario_reapertura_id=:usuario,fecha_cierre=null,usuario_cierre_id=null";
        jdbc.update("UPDATE periodos_fiscales p SET "+campos+" WHERE "+AMBITO+" AND p.id=:id",
            ambito(p).addValue("id",id).addValue("usuario",p.usuarioId()));
    }

    public void reapertura(TenantPrincipal p,UUID id,UUID clave,String motivo,String trace,UUID reversa) {
        var args=ambito(p).addValue("id",id).addValue("clave",clave).addValue("motivo",motivo).addValue("trace",trace)
            .addValue("reversa",reversa).addValue("usuario",p.usuarioId()).addValue("nombre",p.nombre());
        jdbc.update("""
            INSERT INTO reapertura_contable(cierre_id,tenant_id,empresa_id,solicitud,motivo,usuario_id,usuario,trace_id,reversa_id)
            VALUES(:id,:tenant,:empresa,:clave,:motivo,:usuario,:nombre,:trace,:reversa)
            """,args);
        jdbc.update("UPDATE cierre_contable p SET activo=false WHERE "+AMBITO+" AND p.id=:id",args);
    }

    private static Cierre cierre(ResultSet r,int n) throws SQLException {
        var fecha=r.getObject("reapertura_fecha",OffsetDateTime.class);
        var reapertura=fecha==null?null:new Reapertura(r.getString("motivo"),r.getString("usuario"),fecha,
            r.getObject("reversa_id",UUID.class),r.getString("reversa_numero"));
        return new Cierre(r.getObject("id",UUID.class),r.getObject("periodo_id",UUID.class),
            r.getObject("cerrado_en",OffsetDateTime.class),r.getObject("asiento_id",UUID.class),r.getString("asiento_numero"),
            JSON.readValue(r.getString("resumen"),Resumen.class),reapertura);
    }

    private static Cuenta cuenta(ResultSet r,int n) throws SQLException {
        return new Cuenta(r.getObject("id",UUID.class),r.getString("codigo"),r.getString("nombre"));
    }

    private static Periodo periodo(ResultSet r,int n) throws SQLException {
        return new Periodo(r.getObject("id",UUID.class),r.getString("nombre"),r.getObject("fecha_inicial",LocalDate.class),
            r.getObject("fecha_final",LocalDate.class),Estado.valueOf(r.getString("estado")),r.getObject("fecha_cierre",OffsetDateTime.class));
    }

    private static MapSqlParameterSource ambito(TenantPrincipal p) {
        return new MapSqlParameterSource("tenant",p.tenantId()).addValue("empresa",p.empresaId());
    }

    private static MapSqlParameterSource fechas(TenantPrincipal p,LocalDate desde,LocalDate hasta) {
        return ambito(p).addValue("desde",desde).addValue("hasta",hasta);
    }
}
