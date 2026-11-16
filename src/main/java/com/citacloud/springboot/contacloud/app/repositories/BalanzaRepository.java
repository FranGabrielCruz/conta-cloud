package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.dto.BalanzaDto.*;
import com.citacloud.springboot.contacloud.app.dto.CuentaContableDto.Naturaleza;
import com.citacloud.springboot.contacloud.app.dto.CuentaContableDto.Tipo;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BalanzaRepository {
    private static final String JERARQUIA="""
        WITH RECURSIVE catalogo AS (
            SELECT id,padre_id,codigo,nombre,tipo,naturaleza,activo FROM cuenta_contable
            WHERE tenant_id=:tenant AND empresa_id=:empresa
        ), jerarquia AS (
            SELECT c.*,1 AS nivel,ARRAY[CAST(c.codigo AS text)] AS ruta,ARRAY[c.id] AS ancestros,
                   CAST(NULL AS text) AS padre
            FROM catalogo c WHERE c.padre_id IS NULL
            UNION ALL
            SELECT c.*,j.nivel+1,array_append(j.ruta,CAST(c.codigo AS text)),array_append(j.ancestros,c.id),
                   j.codigo || ' — ' || j.nombre
            FROM catalogo c JOIN jerarquia j ON j.id=c.padre_id
            WHERE NOT c.id=ANY(j.ancestros)
        )
        """;
    private static final String BALANZA=JERARQUIA+"""
        , movimientos AS (
            SELECT m.cuenta_id,m.moneda,
                   coalesce(sum(m.debito-m.credito) FILTER (WHERE m.fecha<:desde),0) AS inicial,
                   coalesce(sum(m.debito) FILTER (WHERE m.fecha>=:desde),0) AS debe,
                   coalesce(sum(m.credito) FILTER (WHERE m.fecha>=:desde),0) AS haber
            FROM asiento_movimiento_contable m JOIN catalogo c ON c.id=m.cuenta_id AND c.tipo='MOVIMIENTO'
            WHERE m.tenant_id=:tenant AND m.empresa_id=:empresa AND m.fecha<=:hasta
            GROUP BY m.cuenta_id,m.moneda
        ), consolidados AS (
            SELECT a.id,m.moneda,sum(m.inicial) AS inicial,sum(m.debe) AS debe,sum(m.haber) AS haber
            FROM movimientos m JOIN jerarquia j ON j.id=m.cuenta_id
            CROSS JOIN LATERAL unnest(j.ancestros) a(id)
            GROUP BY a.id,m.moneda
        ), seleccion AS (
            SELECT j.* FROM jerarquia j
            WHERE (CAST(:nivel AS integer) IS NULL OR j.nivel=:nivel)
              AND (:buscar='' OR strpos(lower(j.codigo),lower(:buscar))>0 OR strpos(lower(j.nombre),lower(:buscar))>0)
              AND (:mostrar='TODAS' OR EXISTS (
                  SELECT 1 FROM consolidados c WHERE c.id=j.id AND (
                      (:mostrar IN ('MOVIMIENTOS','MOVIMIENTOS_O_SALDO') AND (c.debe<>0 OR c.haber<>0))
                      OR (:mostrar IN ('SALDO','MOVIMIENTOS_O_SALDO') AND (c.inicial<>0 OR c.inicial+c.debe-c.haber<>0))
                  )
              ))
        )
        """;
    private final NamedParameterJdbcTemplate jdbc;

    public BalanzaRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }

    public int niveles(TenantPrincipal p) {
        return jdbc.queryForObject(JERARQUIA+" SELECT coalesce(max(nivel),0) FROM jerarquia",contexto(p),Integer.class);
    }

    public record Resumen(Totales totales,long total) {}

    public Resumen resumen(TenantPrincipal p,Filtro f) {
        return jdbc.query(BALANZA+"""
            , cuentas_visibles AS (
                SELECT count(*) AS cantidad FROM seleccion
            ), cubiertas AS (
                SELECT DISTINCT j.id
                FROM jerarquia j CROSS JOIN LATERAL unnest(j.ancestros) a(id)
                JOIN seleccion s ON s.id=a.id
            ), totales AS (
                SELECT v.filtrado,m.moneda,sum(greatest(m.inicial,0)) AS inicial_debito,
                       sum(greatest(-m.inicial,0)) AS inicial_credito,sum(m.debe) AS debe,sum(m.haber) AS haber,
                       sum(greatest(m.inicial+m.debe-m.haber,0)) AS final_debito,
                       sum(greatest(-m.inicial-m.debe+m.haber,0)) AS final_credito
                FROM movimientos m LEFT JOIN cubiertas c ON c.id=m.cuenta_id
                CROSS JOIN (VALUES (true),(false)) v(filtrado)
                WHERE NOT v.filtrado OR c.id IS NOT NULL
                GROUP BY v.filtrado,m.moneda
            )
            SELECT cv.cantidad,t.* FROM cuentas_visibles cv LEFT JOIN totales t ON true ORDER BY t.moneda
            """,parametros(p,f),r->{
                var filtrados=new ArrayList<Importes>();
                var generales=new ArrayList<Importes>();
                long total=0;
                while(r.next()) {
                    total=r.getLong("cantidad");
                    String moneda=r.getString("moneda");
                    if(moneda==null) continue;
                    var importes=new Importes(moneda,r.getBigDecimal("inicial_debito"),r.getBigDecimal("inicial_credito"),
                        r.getBigDecimal("debe"),r.getBigDecimal("haber"),r.getBigDecimal("final_debito"),r.getBigDecimal("final_credito"));
                    (r.getBoolean("filtrado")?filtrados:generales).add(importes);
                }
                return new Resumen(new Totales(List.copyOf(filtrados),List.copyOf(generales)),total);
            });
    }

    public List<Cuenta> cuentas(TenantPrincipal p,Filtro f,Integer pagina,int tamano) {
        var args=parametros(p,f);
        String limite="";
        if(pagina!=null) {
            limite=" LIMIT :limite OFFSET :offset";
            args.addValue("limite",tamano).addValue("offset",(long)pagina*tamano);
        }
        var cuentas=new LinkedHashMap<UUID,Cuenta>();
        jdbc.query(BALANZA+", filas AS (SELECT * FROM seleccion ORDER BY ruta"+limite+")"
                +" SELECT f.*,c.moneda,c.inicial,c.debe,c.haber FROM filas f"
                +" LEFT JOIN consolidados c ON c.id=f.id ORDER BY f.ruta,c.moneda",args,r->{
            var id=r.getObject("id",UUID.class);
            var cuenta=cuentas.get(id);
            if(cuenta==null) {
                cuenta=new Cuenta(id,r.getObject("padre_id",UUID.class),r.getString("padre"),r.getString("codigo"),
                    r.getString("nombre"),Tipo.valueOf(r.getString("tipo")),Naturaleza.valueOf(r.getString("naturaleza")),
                    r.getBoolean("activo"),r.getInt("nivel"),new ArrayList<>());
                cuentas.put(id,cuenta);
            }
            String moneda=r.getString("moneda");
            if(moneda!=null) cuenta.importes().add(Importes.cuenta(moneda,r.getBigDecimal("inicial"),
                r.getBigDecimal("debe"),r.getBigDecimal("haber")));
        });
        return cuentas.values().stream().map(c->new Cuenta(c.id(),c.padreId(),c.padre(),c.codigo(),c.nombre(),
            c.tipo(),c.naturaleza(),c.activa(),c.nivel(),List.copyOf(c.importes()))).toList();
    }

    private static MapSqlParameterSource contexto(TenantPrincipal p) {
        return new MapSqlParameterSource("tenant",p.tenantId()).addValue("empresa",p.empresaId());
    }

    private static MapSqlParameterSource parametros(TenantPrincipal p,Filtro f) {
        return contexto(p).addValue("desde",f.fromDate()).addValue("hasta",f.toDate()).addValue("nivel",f.level())
            .addValue("mostrar",f.display().name()).addValue("buscar",f.search());
    }
}
