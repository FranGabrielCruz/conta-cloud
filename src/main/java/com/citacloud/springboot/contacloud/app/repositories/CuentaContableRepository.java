package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.dto.CuentaContableDto.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import java.sql.ResultSet;
import java.sql.SQLException;
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
public class CuentaContableRepository {
    private static final String AMBITO = "c.tenant_id=:tenant AND c.empresa_id=:empresa";
    private static final String FILTRADAS = """
        WITH RECURSIVE visibles(id,padre_id) AS (
            SELECT c.id,c.padre_id FROM cuenta_contable c
            WHERE %s
              AND (CAST(:tipo AS text) IS NULL OR c.tipo=:tipo)
              AND (CAST(:naturaleza AS text) IS NULL OR c.naturaleza=:naturaleza)
              AND (CAST(:activo AS boolean) IS NULL OR c.activo=:activo)
              AND (:buscar='' OR strpos(lower(c.codigo || ' ' || c.nombre),lower(:buscar))>0)
            UNION
            SELECT c.id,c.padre_id FROM cuenta_contable c JOIN visibles v ON c.id=v.padre_id WHERE %s
        )
        """.formatted(AMBITO, AMBITO);
    private static final String RAIZ = "c.padre_id IS NOT DISTINCT FROM CAST(:padre AS uuid)";
    private final NamedParameterJdbcTemplate jdbc;

    public CuentaContableRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Page<Cuenta> raices(TenantPrincipal p, Filtro f, Pageable pagina) {
        var args = filtro(p, f).addValue("padre", null).addValue("offset", pagina.getOffset()).addValue("limite", pagina.getPageSize());
        String origen = " FROM cuenta_contable c WHERE " + AMBITO + " AND " + RAIZ + visible(f, "c");
        long total = jdbc.queryForObject(cte(f) + "SELECT count(*)" + origen, args, Long.class);
        var cuentas = jdbc.query(cte(f) + columnas(f) + origen + " ORDER BY c.codigo LIMIT :limite OFFSET :offset",
            args, (r, n) -> cuenta(r));
        return new PageImpl<>(cuentas, pagina, total);
    }

    public List<Cuenta> hijos(TenantPrincipal p, UUID padre, Filtro f) {
        return jdbc.query(cte(f) + columnas(f) + " FROM cuenta_contable c WHERE " + AMBITO
            + " AND " + RAIZ + visible(f, "c") + " ORDER BY c.codigo", filtro(p, f).addValue("padre", padre), (r, n) -> cuenta(r));
    }

    public Optional<Cuenta> cuenta(TenantPrincipal p, UUID id) { return buscar(p, "c.id", id); }

    public Optional<Cuenta> solicitud(TenantPrincipal p, UUID id) { return buscar(p, "c.solicitud_id", id); }

    private Optional<Cuenta> buscar(TenantPrincipal p, String columna, UUID id) {
        return jdbc.query(columnas(null) + " FROM cuenta_contable c WHERE " + AMBITO + " AND " + columna + "=:id",
            ambito(p).addValue("id", id), (r, n) -> cuenta(r)).stream().findFirst();
    }

    public List<Cuenta> padres(TenantPrincipal p, UUID excluir, String buscar, int offset, int limite) {
        return jdbc.query("""
            WITH RECURSIVE excluidas(id) AS (
                SELECT c.id FROM cuenta_contable c WHERE %s AND c.id=CAST(:excluir AS uuid)
                UNION
                SELECT c.id FROM cuenta_contable c JOIN excluidas e ON e.id=c.padre_id WHERE %s
            )
            """.formatted(AMBITO, AMBITO) + columnas(null) + """
             FROM cuenta_contable c WHERE %s AND c.activo AND c.tipo='AGRUPADORA'
                AND c.id NOT IN (SELECT id FROM excluidas)
                AND (:buscar='' OR strpos(lower(c.codigo || ' ' || c.nombre),lower(:buscar))>0)
             ORDER BY c.codigo LIMIT :limite OFFSET :offset
            """.formatted(AMBITO), ambito(p).addValue("excluir", excluir).addValue("buscar", buscar)
            .addValue("offset", offset).addValue("limite", limite), (r, n) -> cuenta(r));
    }

    public int nivel(TenantPrincipal p, UUID id) {
        return jdbc.queryForObject(ancestros() + "SELECT count(*) FROM padres", ambito(p).addValue("id", id), Integer.class);
    }

    public boolean esAncestro(TenantPrincipal p, UUID cuenta, UUID desde) {
        return jdbc.queryForObject(ancestros() + "SELECT EXISTS(SELECT 1 FROM padres WHERE id=:cuenta)",
            ambito(p).addValue("id", desde).addValue("cuenta", cuenta), Boolean.class);
    }

    private static String ancestros() {
        return """
            WITH RECURSIVE padres(id,padre_id) AS (
                SELECT c.id,c.padre_id FROM cuenta_contable c WHERE %s AND c.id=:id
                UNION
                SELECT c.id,c.padre_id FROM cuenta_contable c JOIN padres p ON c.id=p.padre_id WHERE %s
            )
            """.formatted(AMBITO, AMBITO);
    }

    public List<Uso> usos(TenantPrincipal p, UUID id) {
        return jdbc.query("""
            SELECT modulo,concepto,clase,activo FROM cuenta_contable_uso
            WHERE tenant_id=:tenant AND empresa_id=:empresa AND cuenta_id=:id ORDER BY modulo,concepto
            """, ambito(p).addValue("id", id), (r,n) -> new Uso(r.getString(1),r.getString(2),
                "HISTORICO".equals(r.getString(3)),r.getBoolean(4)));
    }

    public void registrarUso(TenantPrincipal p, UUID cuenta, String modulo, UUID referencia, String concepto, boolean historico) {
        jdbc.update("""
            INSERT INTO cuenta_contable_uso(tenant_id,empresa_id,cuenta_id,modulo,referencia,concepto,clase)
            VALUES(:tenant,:empresa,:cuenta,:modulo,:referencia,:concepto,:clase)
            ON CONFLICT(tenant_id,empresa_id,cuenta_id,modulo,referencia,clase) DO UPDATE
            SET activo=true, concepto=EXCLUDED.concepto
            """, ambito(p).addValue("cuenta", cuenta).addValue("modulo", modulo).addValue("referencia", referencia)
            .addValue("concepto", concepto).addValue("clase", historico ? "HISTORICO" : "INTEGRACION"));
    }

    public void desvincular(TenantPrincipal p, UUID cuenta, String modulo, UUID referencia) {
        jdbc.update("""
            UPDATE cuenta_contable_uso SET activo=false WHERE tenant_id=:tenant AND empresa_id=:empresa AND cuenta_id=:cuenta
              AND modulo=:modulo AND referencia=:referencia AND clase='INTEGRACION'
            """, ambito(p).addValue("cuenta", cuenta).addValue("modulo", modulo).addValue("referencia", referencia));
    }

    public void bloquearEmpresa(TenantPrincipal p) {
        jdbc.queryForObject("SELECT id FROM empresas WHERE tenant_id=:tenant AND id=:empresa FOR NO KEY UPDATE",
            ambito(p), UUID.class);
    }

    public java.math.BigDecimal siguienteSegmento(TenantPrincipal p, UUID padre, String prefijo) {
        return jdbc.queryForObject("""
            INSERT INTO cuenta_contable_secuencia(tenant_id,empresa_id,padre_id,ultimo)
            SELECT :tenant,:empresa,:padre,COALESCE((
                SELECT ultimo FROM cuenta_contable_secuencia
                WHERE tenant_id=:tenant AND empresa_id=:empresa AND padre_id=:padre
            ),(
                SELECT COALESCE(max(substring(codigo FROM length(:prefijo)+1)::numeric),0)
                FROM (
                    SELECT codigo FROM cuenta_contable WHERE tenant_id=:tenant AND empresa_id=:empresa
                    UNION ALL
                    SELECT codigo FROM cuenta_contable_codigo_historico WHERE tenant_id=:tenant AND empresa_id=:empresa
                ) codigos WHERE starts_with(codigo,:prefijo)
                  AND substring(codigo FROM length(:prefijo)+1) ~ '^[0-9]+$'
            )) + 1
            ON CONFLICT(tenant_id,empresa_id,padre_id) DO UPDATE SET ultimo=cuenta_contable_secuencia.ultimo+1
            RETURNING ultimo
            """, ambito(p).addValue("padre", padre).addValue("prefijo", prefijo), java.math.BigDecimal.class);
    }

    public int anchoSegmento(TenantPrincipal p, int nivel) {
        return jdbc.queryForObject("""
            SELECT COALESCE(t.anchos[LEAST(:nivel,cardinality(t.anchos))],2)
            FROM cuenta_contable_plantilla t
            WHERE t.version=COALESCE((SELECT version FROM cuenta_contable_instalacion
                WHERE tenant_id=:tenant AND empresa_id=:empresa),1)
            """, ambito(p).addValue("nivel",nivel), Integer.class);
    }

    public void reservarCodigo(TenantPrincipal p, String codigo) {
        jdbc.update("""
            INSERT INTO cuenta_contable_codigo_historico(tenant_id,empresa_id,codigo)
            VALUES(:tenant,:empresa,:codigo) ON CONFLICT DO NOTHING
            """, ambito(p).addValue("codigo",codigo));
    }

    public void crear(TenantPrincipal p, UUID id, UUID solicitud, Entrada e, String codigo, Clasificacion clasificacion) {
        jdbc.update("""
            INSERT INTO cuenta_contable(id,tenant_id,empresa_id,padre_id,codigo,nombre,tipo,clasificacion,naturaleza,
                descripcion,solicitud_id,creado_por,uso_centro_costo)
            VALUES(:id,:tenant,:empresa,:padre,:codigo,:nombre,:tipo,:clasificacion,:naturaleza,:descripcion,:solicitud,:usuario,
                coalesce(:uso,'OPCIONAL'))
            """, datos(p,id,e,codigo,clasificacion).addValue("solicitud",solicitud).addValue("usuario",p.usuarioId()));
    }

    public void editar(TenantPrincipal p, UUID id, Entrada e, String codigo, Clasificacion clasificacion) {
        jdbc.update("""
            UPDATE cuenta_contable c SET padre_id=:padre,codigo=:codigo,nombre=:nombre,tipo=:tipo,
                clasificacion=:clasificacion,naturaleza=:naturaleza,descripcion=:descripcion,version=version+1,
                uso_centro_costo=coalesce(:uso,CASE WHEN c.tipo<>:tipo THEN 'OPCIONAL' ELSE c.uso_centro_costo END)
            WHERE c.id=:id AND
            """ + AMBITO, datos(p,id,e,codigo,clasificacion));
    }

    public void estado(TenantPrincipal p, UUID id, boolean activo) {
        jdbc.update("UPDATE cuenta_contable c SET activo=:activo,version=version+1 WHERE c.id=:id AND " + AMBITO,
            ambito(p).addValue("id",id).addValue("activo",activo));
    }

    public boolean hijosActivos(TenantPrincipal p, UUID id) {
        return jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM cuenta_contable c WHERE " + AMBITO
            + " AND c.padre_id=:id AND c.activo)", ambito(p).addValue("id",id), Boolean.class);
    }

    public List<Linea> reporte(TenantPrincipal p, Filtro f) {
        String with = f.restringido() ? FILTRADAS.stripTrailing() + ",\n" : "WITH RECURSIVE ";
        return jdbc.query(with + """
            arbol(id,nivel,ruta) AS (
                SELECT c.id,1,ARRAY[c.codigo::text] FROM cuenta_contable c
                WHERE %s AND c.padre_id IS NULL %s
                UNION ALL
                SELECT c.id,a.nivel+1,a.ruta || c.codigo FROM cuenta_contable c JOIN arbol a ON c.padre_id=a.id
                WHERE %s %s
            )
            """.formatted(AMBITO,visible(f,"c"),AMBITO,visible(f,"c")) + columnas(f)
            + ", a.nivel FROM cuenta_contable c JOIN arbol a ON a.id=c.id WHERE " + AMBITO + " ORDER BY a.ruta",
            filtro(p,f),(r,n) -> new Linea(cuenta(r),r.getInt("nivel")));
    }

    private static String columnas(Filtro f) {
        return """
            SELECT c.*,EXISTS(SELECT 1 FROM cuenta_contable h WHERE h.tenant_id=c.tenant_id
                AND h.empresa_id=c.empresa_id AND h.padre_id=c.id %s) hijos
            """.formatted(visible(f,"h"));
    }

    private static Cuenta cuenta(ResultSet r) throws SQLException {
        return new Cuenta(r.getObject("id", UUID.class), r.getObject("padre_id", UUID.class), r.getString("codigo"),
            r.getString("nombre"), Tipo.valueOf(r.getString("tipo")), Clasificacion.valueOf(r.getString("clasificacion")),
            Naturaleza.valueOf(r.getString("naturaleza")), r.getBoolean("activo"), r.getString("descripcion"),
            r.getLong("version"), r.getBoolean("hijos"), r.getBoolean("estructural"),
            "MOVIMIENTO".equals(r.getString("tipo"))?com.citacloud.springboot.contacloud.app.dto.CentroCostoDto.Uso.valueOf(r.getString("uso_centro_costo"))
                :com.citacloud.springboot.contacloud.app.dto.CentroCostoDto.Uso.NO_PERMITIDO);
    }

    private static String cte(Filtro f) { return f.restringido() ? FILTRADAS : ""; }

    private static String visible(Filtro f, String alias) {
        return f != null && f.restringido() ? " AND " + alias + ".id IN (SELECT id FROM visibles)" : "";
    }

    private static MapSqlParameterSource ambito(TenantPrincipal p) {
        return new MapSqlParameterSource("tenant", p.tenantId()).addValue("empresa", p.empresaId());
    }

    private static MapSqlParameterSource filtro(TenantPrincipal p, Filtro f) {
        return ambito(p).addValue("tipo", f.tipo() == null ? null : f.tipo().name())
            .addValue("naturaleza", f.naturaleza() == null ? null : f.naturaleza().name())
            .addValue("activo", f.activo()).addValue("buscar", f.buscar());
    }

    private static MapSqlParameterSource datos(TenantPrincipal p, UUID id, Entrada e, String codigo, Clasificacion clasificacion) {
        return ambito(p).addValue("id", id).addValue("padre", e.padre()).addValue("codigo", codigo)
            .addValue("nombre", e.nombre()).addValue("tipo", e.tipo().name()).addValue("clasificacion", clasificacion.name())
            .addValue("naturaleza", e.naturaleza().name()).addValue("descripcion", e.descripcion())
            .addValue("uso",e.tipo()==Tipo.AGRUPADORA?"NO_PERMITIDO":e.usoCentroCosto()==null?null:e.usoCentroCosto().name());
    }
}
