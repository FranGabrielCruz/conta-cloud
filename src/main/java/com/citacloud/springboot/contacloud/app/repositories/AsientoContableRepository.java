package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.dto.CentroCostoDto;

import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.*;
import com.citacloud.springboot.contacloud.app.dto.DepreciacionDto.Empresa;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class AsientoContableRepository {
    public boolean monedaActiva(TenantPrincipal p,UUID moneda) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM monedas WHERE "+AMBITO+" AND id=:id AND activo)",ambito(p).addValue("id",moneda),Boolean.class));
    }
    public Optional<java.math.BigDecimal> tasaExacta(TenantPrincipal p,UUID origen,UUID destino,LocalDate fecha) {
        var args=ambito(p).addValue("origen",origen).addValue("destino",destino).addValue("fecha",fecha);
        var directa=jdbc.query("SELECT tasa FROM tasas_cambio WHERE "+AMBITO+" AND moneda_origen_id=:origen AND moneda_destino_id=:destino AND fecha=:fecha AND activo",args,(r,n)->r.getBigDecimal(1));
        if(!directa.isEmpty())return Optional.of(directa.getFirst());
        var inversa=jdbc.query("SELECT tasa FROM tasas_cambio WHERE "+AMBITO+" AND moneda_origen_id=:destino AND moneda_destino_id=:origen AND fecha=:fecha AND activo",args,(r,n)->r.getBigDecimal(1));
        return inversa.stream().filter(t->t.signum()>0).findFirst().map(t->java.math.BigDecimal.ONE.divide(t,8,java.math.RoundingMode.HALF_UP));
    }
    public void registrarConversion(TenantPrincipal p,UUID id,UUID moneda,java.math.BigDecimal tasa) {
        jdbc.update("UPDATE asiento_contable SET fuente_moneda_id=:moneda,fuente_tasa_cambio=:tasa WHERE "+AMBITO+" AND id=:id AND estado='BORRADOR'",
            ambito(p).addValue("id",id).addValue("moneda",moneda).addValue("tasa",tasa));
    }
    public void registrarEvento(TenantPrincipal p,UUID id,long version,String traceId,String datos) {
        if(version<0||traceId==null||traceId.isBlank()||traceId.length()>120)
            throw new IllegalArgumentException("Metadatos de evento contable inválidos.");
        jdbc.update("UPDATE asiento_contable SET fuente_version=:version,fuente_trace_id=:trace,fuente_datos=CAST(:datos AS jsonb) WHERE "+AMBITO+" AND id=:id AND estado='BORRADOR'",
            ambito(p).addValue("id",id).addValue("version",version).addValue("trace",traceId).addValue("datos",datos));
    }
    private static final String AMBITO="tenant_id=:tenant AND empresa_id=:empresa";
    private static final String ASIENTO="a.tenant_id=:tenant AND a.empresa_id=:empresa";
    private static final String TOTALES="""
        LEFT JOIN LATERAL (SELECT COALESCE(sum(l.debito),0) debito,COALESCE(sum(l.credito),0) credito
            FROM asiento_contable_linea l WHERE (l.tenant_id,l.empresa_id,l.asiento_id)=(a.tenant_id,a.empresa_id,a.id)) m ON true
        """;
    private static final String FILTROS="""
         AND (CAST(:desde AS date) IS NULL OR a.fecha>=:desde)
         AND (CAST(:hasta AS date) IS NULL OR a.fecha<=:hasta)
         AND (CAST(:tipo AS text) IS NULL OR a.tipo=:tipo)
         AND (CAST(:estado AS text) IS NULL OR a.estado=:estado)
         AND (CAST(:origen AS text) IS NULL OR a.origen=:origen)
         AND (:buscar='' OR strpos(lower(concat_ws(' ',a.numero,a.concepto,a.referencia,a.fuente_numero)),lower(:buscar))>0)
        """;
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private final NamedParameterJdbcTemplate jdbc;

    public AsientoContableRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }

    public Page<Fila> listar(TenantPrincipal p,Filtro f,Pageable pagina) {
        var args=ambito(p).addValue("desde",f.desde()).addValue("hasta",f.hasta()).addValue("tipo",nombre(f.tipo()))
            .addValue("estado",nombre(f.estado())).addValue("origen",nombre(f.origen())).addValue("buscar",f.buscar())
            .addValue("limite",pagina.getPageSize()).addValue("offset",pagina.getOffset());
        String where=" WHERE "+ASIENTO+FILTROS;
        long total=jdbc.queryForObject("SELECT count(*) FROM asiento_contable a"+where,args,Long.class);
        var filas=jdbc.query("SELECT a.*,m.debito,m.credito FROM (SELECT a.* FROM asiento_contable a"+where
            +" ORDER BY a.fecha DESC,a.numero DESC LIMIT :limite OFFSET :offset) a "+TOTALES+" ORDER BY a.fecha DESC,a.numero DESC",
            args,(r,n)->fila(r));
        return new PageImpl<>(filas,pagina,total);
    }

    public Optional<Detalle> detalle(TenantPrincipal p,UUID id) {
        var args=ambito(p).addValue("id",id);
        return jdbc.query("""
            SELECT a.*,m.debito,m.credito,original.numero original_numero,reversa.numero reversa_numero
            FROM asiento_contable a
            LEFT JOIN asiento_contable original ON (original.tenant_id,original.empresa_id,original.id)=(a.tenant_id,a.empresa_id,a.reversa_de)
            LEFT JOIN asiento_contable reversa ON (reversa.tenant_id,reversa.empresa_id,reversa.reversa_de)=(a.tenant_id,a.empresa_id,a.id)
            """+TOTALES+" WHERE "+ASIENTO+" AND a.id=:id",args,(r,n)->{
                var f=fila(r);
                var tipo=r.getString("fuente_tipo");
                var snapshot=r.getString("emisor");
                return new Detalle(f,new Moneda(uuid(r,"moneda_id"),f.moneda()),r.getString("referencia"),r.getString("observacion"),
                    List.of(),tipo==null?null:new Fuente(f.origen(),tipo,uuid(r,"fuente_id"),r.getString("fuente_numero"),r.getString("fuente_evento")),
                    uuid(r,"reversa_de"),r.getString("original_numero"),r.getString("reversa_numero"),r.getString("motivo"),
                    r.getLong("version"),r.getObject("creado_en",OffsetDateTime.class),r.getString("creador"),
                    r.getObject("contabilizado_en",OffsetDateTime.class),r.getString("contabilizador"),uuid(r,"contabilizacion"),
                    snapshot==null?null:JSON.readValue(snapshot,Empresa.class));
            }).stream().findFirst().map(d->new Detalle(d.fila(),d.moneda(),d.referencia(),d.observacion(),lineas(p,id),
                d.fuente(),d.reversaDe(),d.numeroOriginal(),d.numeroReversion(),d.motivo(),d.version(),d.creado(),d.creador(),
                d.contabilizado(),d.contabilizador(),d.contabilizacion(),d.emisor()));
    }

    private List<Linea> lineas(TenantPrincipal p,UUID id) {
        return jdbc.query("""
            SELECT l.*,a.uso_centro_costo,c.codigo AS centro_codigo,c.nombre AS centro_nombre,c.activo AS centro_activo
            FROM asiento_contable_linea l
            JOIN cuenta_contable a ON (a.tenant_id,a.empresa_id,a.id)=(l.tenant_id,l.empresa_id,l.cuenta_id)
            LEFT JOIN centro_costo c ON (c.tenant_id,c.empresa_id,c.id)=(l.tenant_id,l.empresa_id,l.centro_costo_id)
            WHERE l.tenant_id=:tenant AND l.empresa_id=:empresa AND l.asiento_id=:id ORDER BY l.orden
            """,
            ambito(p).addValue("id",id),(r,n)->new Linea(new Cuenta(uuid(r,"cuenta_id"),r.getString("codigo"),
                r.getString("cuenta"),true,true,CentroCostoDto.Uso.valueOf(r.getString("uso_centro_costo"))),
                r.getString("descripcion"),r.getBigDecimal("debito"),r.getBigDecimal("credito"),
                centro(r,"centro_costo_id","centro_")));
    }

    public Map<UUID,CentroCostoDto.Referencia> centros(TenantPrincipal p,Set<UUID> ids) {
        if(ids.isEmpty()) return Map.of();
        var centros=new java.util.HashMap<UUID,CentroCostoDto.Referencia>();
        jdbc.query("SELECT id,codigo,nombre,activo FROM centro_costo WHERE "+AMBITO+" AND id IN (:ids)",
            ambito(p).addValue("ids",ids),r->{var c=centro(r,"id",""); centros.put(c.id(),c);});
        return centros;
    }

    public List<CentroCostoDto.Referencia> buscarCentros(TenantPrincipal p,String buscar,int offset,int limite) {
        return jdbc.query("SELECT id,codigo,nombre,activo FROM centro_costo WHERE "+AMBITO+"""
             AND activo AND (:buscar='' OR strpos(lower(codigo || ' ' || nombre),lower(:buscar))>0)
             ORDER BY numero LIMIT :limite OFFSET :offset
            """,ambito(p).addValue("buscar",buscar).addValue("offset",offset).addValue("limite",limite),(r,n)->centro(r,"id",""));
    }

    private static CentroCostoDto.Referencia centro(ResultSet r,String id,String prefijo) throws SQLException {
        UUID uuid=uuid(r,id);
        return uuid==null?null:new CentroCostoDto.Referencia(uuid,r.getString(prefijo+"codigo"),r.getString(prefijo+"nombre"),
            r.getBoolean(prefijo+"activo"));
    }

    public Optional<UUID> porSolicitud(TenantPrincipal p,UUID solicitud) {
        return jdbc.query("SELECT id FROM asiento_contable WHERE "+AMBITO+" AND solicitud=:solicitud",
            ambito(p).addValue("solicitud",solicitud),(r,n)->uuid(r,"id")).stream().findFirst();
    }

    public Optional<UUID> porFuente(TenantPrincipal p,Fuente f) {
        return jdbc.query("SELECT id FROM asiento_contable WHERE "+AMBITO
            +" AND origen=:origen AND fuente_tipo=:tipo AND fuente_id=:id AND fuente_evento=:evento",
            ambito(p).addValue("origen",f.origen().name()).addValue("tipo",f.tipo()).addValue("id",f.id())
                .addValue("evento",f.evento()),(r,n)->uuid(r,"id")).stream().findFirst();
    }

    public Optional<UUID> reversa(TenantPrincipal p,UUID id) {
        return jdbc.query("SELECT id FROM asiento_contable WHERE "+AMBITO+" AND reversa_de=:id",
            ambito(p).addValue("id",id),(r,n)->uuid(r,"id")).stream().findFirst();
    }

    public Optional<Moneda> monedaBase(TenantPrincipal p) {
        return jdbc.query("SELECT id,codigo_iso FROM monedas WHERE "+AMBITO+" AND moneda_base AND activo",
            ambito(p),(r,n)->new Moneda(uuid(r,"id"),r.getString("codigo_iso"))).stream().findFirst();
    }

    public List<Cuenta> buscar(TenantPrincipal p,String texto,int offset,int limite) {
        return jdbc.query("SELECT * FROM cuenta_contable WHERE "+AMBITO+"""
             AND activo AND tipo='MOVIMIENTO'
             AND (:buscar='' OR strpos(lower(codigo||' '||nombre),lower(:buscar))>0)
             ORDER BY codigo LIMIT :limite OFFSET :offset
            """,ambito(p).addValue("buscar",texto).addValue("offset",offset).addValue("limite",limite),(r,n)->cuenta(r));
    }

    public Map<UUID,Cuenta> cuentas(TenantPrincipal p,Set<UUID> ids) {
        if(ids.isEmpty()) return Map.of();
        var result=new HashMap<UUID,Cuenta>();
        jdbc.query("SELECT * FROM cuenta_contable WHERE "+AMBITO+" AND id IN (:ids)",ambito(p).addValue("ids",ids),r->{
            var c=cuenta(r);
            result.put(c.id(),c);
        });
        return result;
    }

    public Map<String,UUID> claves(TenantPrincipal p,Set<String> claves) {
        if(claves.isEmpty()) return Map.of();
        var result=new HashMap<String,UUID>();
        jdbc.query("SELECT clave,cuenta_id FROM cuenta_contable_clave_empresa WHERE "+AMBITO+" AND clave IN (:claves)",
            ambito(p).addValue("claves",claves),r->{result.put(r.getString(1),uuid(r,"cuenta_id"));});
        return result;
    }

    public boolean periodoAbierto(TenantPrincipal p,LocalDate fecha) {
        return !jdbc.query("SELECT id FROM periodos_fiscales WHERE "+AMBITO
            +" AND estado='ABIERTO' AND fecha_inicial<=:fecha AND fecha_final>=:fecha FOR SHARE",
            ambito(p).addValue("fecha",fecha),(r,n)->r.getObject(1,UUID.class)).isEmpty();
    }

    public boolean periodoCerrado(TenantPrincipal p,LocalDate fecha) {
        return !jdbc.query("SELECT id FROM periodos_fiscales WHERE "+AMBITO
            +" AND estado<>'ABIERTO' AND fecha_inicial<=:fecha AND fecha_final>=:fecha FOR SHARE",
            ambito(p).addValue("fecha",fecha),(r,n)->r.getObject(1,UUID.class)).isEmpty();
    }

    public String siguiente(TenantPrincipal p) {
        long numero=jdbc.queryForObject("""
            INSERT INTO asiento_secuencia(tenant_id,empresa_id,ultimo) VALUES(:tenant,:empresa,1)
            ON CONFLICT(tenant_id,empresa_id) DO UPDATE SET ultimo=asiento_secuencia.ultimo+1 RETURNING ultimo
            """,ambito(p),Long.class);
        return String.format(Locale.ROOT,"AS-%06d",numero);
    }

    public void crear(TenantPrincipal p,UUID id,String numero,Entrada e,Moneda moneda,Fuente fuente,UUID reversa,String motivo,UUID solicitud) {
        var args=datos(p,id,e,moneda).addValue("numero",numero).addValue("origen",fuente==null?"MANUAL":fuente.origen().name())
            .addValue("fuenteTipo",fuente==null?null:fuente.tipo()).addValue("fuenteId",fuente==null?null:fuente.id())
            .addValue("fuenteNumero",fuente==null?null:fuente.numero()).addValue("fuenteEvento",fuente==null?null:fuente.evento())
            .addValue("reversa",reversa).addValue("motivo",motivo).addValue("solicitud",solicitud);
        jdbc.update("""
            INSERT INTO asiento_contable(id,tenant_id,empresa_id,numero,fecha,tipo,origen,moneda_id,moneda,concepto,referencia,
                observacion,fuente_tipo,fuente_id,fuente_numero,fuente_evento,reversa_de,motivo,solicitud,creado_por,creador)
            VALUES(:id,:tenant,:empresa,:numero,:fecha,:tipo,:origen,:monedaId,:moneda,:concepto,:referencia,
                :observacion,:fuenteTipo,:fuenteId,:fuenteNumero,:fuenteEvento,:reversa,:motivo,:solicitud,:usuario,:nombre)
            """,args);
    }

    public void editar(TenantPrincipal p,UUID id,Entrada e,Moneda moneda) {
        jdbc.update("""
            UPDATE asiento_contable SET fecha=:fecha,tipo=:tipo,moneda_id=:monedaId,moneda=:moneda,concepto=:concepto,
                referencia=:referencia,observacion=:observacion,version=version+1 WHERE id=:id AND
            """+AMBITO,datos(p,id,e,moneda));
    }

    public void reemplazarLineas(TenantPrincipal p,UUID id,List<Linea> lineas) {
        jdbc.update("DELETE FROM asiento_contable_linea WHERE "+AMBITO+" AND asiento_id=:id",ambito(p).addValue("id",id));
        var batch=new MapSqlParameterSource[lineas.size()];
        for(int i=0;i<lineas.size();i++) {
            var l=lineas.get(i);
            batch[i]=ambito(p).addValue("id",id).addValue("orden",i+1).addValue("cuentaId",l.cuenta().id())
                .addValue("codigo",l.cuenta().codigo()).addValue("cuenta",l.cuenta().nombre()).addValue("descripcion",l.descripcion())
                .addValue("debito",l.debito()).addValue("credito",l.credito())
                .addValue("centro",l.centroCosto()==null?null:l.centroCosto().id());
        }
        if(batch.length>0) jdbc.batchUpdate("""
            INSERT INTO asiento_contable_linea(tenant_id,empresa_id,asiento_id,orden,cuenta_id,codigo,cuenta,descripcion,debito,credito,centro_costo_id)
            VALUES(:tenant,:empresa,:id,:orden,:cuentaId,:codigo,:cuenta,:descripcion,:debito,:credito,:centro)
            """,batch);
    }

    public void origenSistema(TenantPrincipal p,UUID id) {
        jdbc.update("UPDATE asiento_contable SET origen='SISTEMA' WHERE estado='BORRADOR' AND id=:id AND "+AMBITO,
            ambito(p).addValue("id",id));
    }

    public void contabilizar(TenantPrincipal p,UUID id,UUID solicitud,Empresa emisor) {
        jdbc.update("""
            UPDATE asiento_contable SET estado='CONTABILIZADO',contabilizacion=:solicitud,contabilizado_en=current_timestamp,
                contabilizado_por=:usuario,contabilizador=:nombre,emisor=CAST(:emisor AS jsonb),version=version+1
            WHERE id=:id AND
            """+AMBITO,ambito(p).addValue("id",id).addValue("solicitud",solicitud).addValue("usuario",p.usuarioId())
                .addValue("nombre",p.nombre()).addValue("emisor",JSON.writeValueAsString(emisor)));
        jdbc.update("""
            INSERT INTO cuenta_contable_uso(tenant_id,empresa_id,cuenta_id,modulo,referencia,concepto,clase)
            SELECT DISTINCT l.tenant_id,l.empresa_id,l.cuenta_id,'Contabilidad',a.id,a.numero,'HISTORICO'
            FROM asiento_contable_linea l JOIN asiento_contable a ON (a.tenant_id,a.empresa_id,a.id)=(l.tenant_id,l.empresa_id,l.asiento_id)
            WHERE a.id=:id AND
            """+ASIENTO,ambito(p).addValue("id",id));
    }

    public void marcarReversado(TenantPrincipal p,UUID id) {
        jdbc.update("UPDATE asiento_contable SET estado='REVERSADO',version=version+1 WHERE id=:id AND "+AMBITO,
            ambito(p).addValue("id",id));
    }

    private static Cuenta cuenta(ResultSet r)throws SQLException {
        return new Cuenta(uuid(r,"id"),r.getString("codigo"),r.getString("nombre"),r.getBoolean("activo"),
            "MOVIMIENTO".equals(r.getString("tipo")),CentroCostoDto.Uso.valueOf(r.getString("uso_centro_costo")));
    }

    private static Fila fila(ResultSet r)throws SQLException {
        return new Fila(uuid(r,"id"),r.getString("numero"),r.getObject("fecha",LocalDate.class),Tipo.valueOf(r.getString("tipo")),
            Origen.valueOf(r.getString("origen")),r.getString("concepto"),r.getString("moneda"),Estado.valueOf(r.getString("estado")),
            new Total(r.getBigDecimal("debito"),r.getBigDecimal("credito")));
    }

    private static MapSqlParameterSource datos(TenantPrincipal p,UUID id,Entrada e,Moneda moneda) {
        return ambito(p).addValue("id",id).addValue("fecha",e.fecha()).addValue("tipo",e.tipo().name())
            .addValue("monedaId",moneda.id()).addValue("moneda",moneda.codigo())
            .addValue("concepto",e.concepto()).addValue("referencia",e.referencia()).addValue("observacion",e.observacion())
            .addValue("usuario",p.usuarioId()).addValue("nombre",p.nombre());
    }

    private static MapSqlParameterSource ambito(TenantPrincipal p) {
        return new MapSqlParameterSource("tenant",p.tenantId()).addValue("empresa",p.empresaId());
    }
    private static UUID uuid(ResultSet r,String campo)throws SQLException {return r.getObject(campo,UUID.class);}
    private static String nombre(Enum<?> v){return v==null?null:v.name();}
}
