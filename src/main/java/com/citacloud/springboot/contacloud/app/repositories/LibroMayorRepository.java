package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.Estado;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.Origen;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.Tipo;
import com.citacloud.springboot.contacloud.app.dto.CuentaContableDto;
import com.citacloud.springboot.contacloud.app.dto.LibroMayorDto.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LibroMayorRepository {
    private static final String CONSULTABLE = """
        (c.activo OR EXISTS (
            WITH RECURSIVE rama(id,tipo) AS (
                SELECT c.id,c.tipo
                UNION
                SELECT hija.id,hija.tipo FROM cuenta_contable hija JOIN rama r ON hija.padre_id=r.id
                WHERE hija.tenant_id=:tenant AND hija.empresa_id=:empresa AND r.tipo='AGRUPADORA'
            )
            SELECT 1 FROM asiento_movimiento_contable h JOIN rama r ON r.id=h.cuenta_id
            WHERE h.tenant_id=:tenant AND h.empresa_id=:empresa AND r.tipo='MOVIMIENTO'
        ))
        """;
    private static final String CUENTAS = """
        SELECT c.id,c.codigo,c.nombre,c.tipo,c.naturaleza,c.activo
        FROM cuenta_contable c WHERE c.tenant_id=:tenant AND c.empresa_id=:empresa
        """;
    private static final String ORDEN = "fecha,length(numero),numero,asiento_id,orden";
    private static final String BASE = """
        WITH RECURSIVE descendientes(id,tipo) AS (
            SELECT id,tipo FROM cuenta_contable
            WHERE tenant_id=:tenant AND empresa_id=:empresa AND id=:cuenta
            UNION
            SELECT c.id,c.tipo FROM cuenta_contable c JOIN descendientes d ON c.padre_id=d.id
            WHERE c.tenant_id=:tenant AND c.empresa_id=:empresa AND d.tipo='AGRUPADORA'
        ), preliminar AS (
            SELECT m.moneda,sum(m.debito-m.credito) AS saldo
            FROM asiento_movimiento_contable m JOIN descendientes d ON d.id=m.cuenta_id
            WHERE m.tenant_id=:tenant AND m.empresa_id=:empresa AND d.tipo='MOVIMIENTO' AND m.fecha<:desde
            GROUP BY m.moneda
        ), periodo AS (
            SELECT m.asiento_id,m.orden,m.cuenta_id,m.codigo,m.cuenta,m.descripcion,m.debito,m.credito,
                   a.numero,a.fecha,a.moneda,a.tipo,a.origen,a.estado,a.concepto,a.referencia,a.fuente_numero,
                   (CAST(:tipo AS text) IS NULL OR a.tipo=:tipo)
                       AND (CAST(:origen AS text) IS NULL OR a.origen=:origen)
                       AND (:buscar='' OR strpos(lower(concat_ws(' ',a.numero,a.concepto,a.referencia,
                           a.fuente_numero,m.descripcion)),lower(:buscar))>0) AS visible
            FROM asiento_contable_linea m JOIN descendientes d ON d.id=m.cuenta_id
            JOIN asiento_contable a ON (a.tenant_id,a.empresa_id,a.id)=(m.tenant_id,m.empresa_id,m.asiento_id)
            WHERE m.tenant_id=:tenant AND m.empresa_id=:empresa AND d.tipo='MOVIMIENTO'
              AND a.estado IN ('CONTABILIZADO','REVERSADO') AND a.fecha BETWEEN :desde AND :hasta
        )
        """;

    private final NamedParameterJdbcTemplate jdbc;

    public LibroMayorRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Cuenta> cuentas(TenantPrincipal principal) {
        return jdbc.query(CUENTAS + " AND "+CONSULTABLE+" ORDER BY c.codigo,c.id", contexto(principal), LibroMayorRepository::cuenta);
    }

    public Optional<Cuenta> cuenta(TenantPrincipal principal, UUID id) {
        return jdbc.query(CUENTAS + " AND c.id=:cuenta", contexto(principal).addValue("cuenta",id),
            LibroMayorRepository::cuenta).stream().findFirst();
    }

    public List<Resumen> resumen(TenantPrincipal principal, Filtro filtro) {
        return jdbc.query(BASE + """
            , totales AS (
                SELECT moneda,count(*) FILTER (WHERE visible) AS movimientos,
                       sum(debito) FILTER (WHERE visible) AS debe,
                       sum(credito) FILTER (WHERE visible) AS haber,
                       sum(debito-credito) AS neto
                FROM periodo GROUP BY moneda
            )
            SELECT moneda,coalesce(saldo,0) AS inicial,coalesce(debe,0) AS debe,
                   coalesce(haber,0) AS haber,coalesce(saldo,0)+coalesce(neto,0) AS cierre,
                   coalesce(movimientos,0) AS movimientos
            FROM preliminar FULL JOIN totales USING (moneda) ORDER BY moneda
            """, parametros(principal,filtro), (r,n) -> {
                var debe=r.getBigDecimal("debe");
                var haber=r.getBigDecimal("haber");
                return new Resumen(r.getString("moneda"),Saldo.de(r.getBigDecimal("inicial")),debe,haber,
                    Saldo.de(debe.subtract(haber)),Saldo.de(r.getBigDecimal("cierre")),r.getLong("movimientos"));
            });
    }

    public List<Movimiento> movimientos(TenantPrincipal principal, Filtro filtro, Integer pagina, int tamano) {
        var args=parametros(principal,filtro);
        // La ventana incluye todo el período. Los filtros y la paginación se aplican al resultado.
        String sql=BASE + """
            , calculados AS (
                SELECT p.*,coalesce(i.saldo,0)+sum(debito-credito) OVER (
                    PARTITION BY p.moneda ORDER BY %s ROWS UNBOUNDED PRECEDING
                ) AS acumulado
                FROM periodo p LEFT JOIN preliminar i ON i.moneda=p.moneda
            )
            SELECT * FROM calculados WHERE visible ORDER BY %s
            """.formatted(ORDEN,ORDEN);
        if (pagina!=null) {
            sql+=" LIMIT :limite OFFSET :offset";
            args.addValue("limite",tamano).addValue("offset",(long)pagina*tamano);
        }
        return jdbc.query(sql,args,(r,n)->new Movimiento(r.getObject("asiento_id",UUID.class),r.getString("numero"),
            r.getObject("fecha",LocalDate.class),r.getString("concepto"),r.getString("referencia"),
            r.getString("fuente_numero"),r.getString("descripcion"),Tipo.valueOf(r.getString("tipo")),
            Origen.valueOf(r.getString("origen")),Estado.valueOf(r.getString("estado")),r.getObject("cuenta_id",UUID.class),
            r.getString("codigo"),r.getString("cuenta"),r.getString("moneda"),r.getBigDecimal("debito"),
            r.getBigDecimal("credito"),Saldo.de(r.getBigDecimal("acumulado"))));
    }

    private static Cuenta cuenta(ResultSet r,int fila) throws SQLException {
        return new Cuenta(r.getObject("id",UUID.class),r.getString("codigo"),r.getString("nombre"),
            CuentaContableDto.Tipo.valueOf(r.getString("tipo")),
            CuentaContableDto.Naturaleza.valueOf(r.getString("naturaleza")),r.getBoolean("activo"));
    }

    private static MapSqlParameterSource contexto(TenantPrincipal p) {
        return new MapSqlParameterSource("tenant",p.tenantId()).addValue("empresa",p.empresaId());
    }

    private static MapSqlParameterSource parametros(TenantPrincipal p,Filtro f) {
        return contexto(p).addValue("cuenta",f.accountId()).addValue("desde",f.fromDate()).addValue("hasta",f.toDate())
            .addValue("tipo",f.type()==null?null:f.type().name()).addValue("origen",f.origin()==null?null:f.origin().name())
            .addValue("buscar",f.search());
    }
}
