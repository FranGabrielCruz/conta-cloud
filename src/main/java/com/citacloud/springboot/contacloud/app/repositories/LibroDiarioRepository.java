package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.Estado;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.Origen;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.Tipo;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.Total;
import com.citacloud.springboot.contacloud.app.dto.LibroDiarioDto.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LibroDiarioRepository {
    private static final String ORDEN="a.fecha, length(a.numero), a.numero, a.id";
    private static final String FILTRADOS="""
        SELECT a.id,a.tenant_id,a.empresa_id,a.numero,a.fecha,a.tipo,a.origen,a.estado,a.concepto,a.referencia,a.fuente_numero,a.moneda
        FROM asiento_contable a
        WHERE a.tenant_id=:tenant AND a.empresa_id=:empresa AND a.estado IN ('CONTABILIZADO','REVERSADO')
          AND a.fecha BETWEEN :desde AND :hasta
          AND (CAST(:tipo AS text) IS NULL OR a.tipo=:tipo)
          AND (CAST(:origen AS text) IS NULL OR a.origen=:origen)
          AND (:buscar='' OR strpos(lower(concat_ws(' ',a.numero,a.concepto,a.referencia,a.fuente_numero)),lower(:buscar))>0
            OR EXISTS (
                SELECT 1 FROM asiento_contable_linea b
                WHERE (b.tenant_id,b.empresa_id,b.asiento_id)=(a.tenant_id,a.empresa_id,a.id)
                  AND strpos(lower(b.codigo || ' ' || b.cuenta),lower(:buscar))>0
            ))
        """;
    private static final String MOVIMIENTOS="""
        JOIN asiento_contable_linea l ON (l.tenant_id,l.empresa_id,l.asiento_id)=(a.tenant_id,a.empresa_id,a.id)
        """;
    private final NamedParameterJdbcTemplate jdbc;

    public LibroDiarioRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }

    public List<Asiento> asientos(TenantPrincipal p,Filtro f,Integer pagina,Integer tamano) {
        var args=parametros(p,f);
        String limite="";
        if(pagina!=null) {
            limite=" ORDER BY "+ORDEN+" LIMIT :limite OFFSET :offset";
            args.addValue("limite",tamano).addValue("offset",(long)pagina*tamano);
        }
        var bloques=new LinkedHashMap<UUID,Asiento>();
        jdbc.query("WITH seleccion AS ("+FILTRADOS+limite+") SELECT a.*,l.codigo,l.cuenta,l.descripcion,l.debito,l.credito"
                +" FROM seleccion a "+MOVIMIENTOS+" ORDER BY "+ORDEN+", l.orden",
            args,r->{
                var id=r.getObject("id",UUID.class);
                var a=bloques.get(id);
                if(a==null) {
                    a=cabecera(r,id);
                    bloques.put(id,a);
                }
                a.movimientos().add(new Movimiento(r.getString("codigo"),r.getString("cuenta"),
                    r.getString("descripcion"),r.getBigDecimal("debito"),r.getBigDecimal("credito")));
            });
        return bloques.values().stream().map(a->{
            var debe=java.math.BigDecimal.ZERO;
            var haber=java.math.BigDecimal.ZERO;
            for(var l:a.movimientos()) {
                debe=debe.add(l.debito());
                haber=haber.add(l.credito());
            }
            return new Asiento(a.id(),a.numero(),a.fecha(),a.tipo(),a.origen(),a.estado(),a.concepto(),a.referencia(),
                a.documento(),a.moneda(),List.copyOf(a.movimientos()),new Total(debe,haber));
        }).toList();
    }

    public List<TotalMoneda> totales(TenantPrincipal p,Filtro f) {
        return jdbc.query("WITH seleccion AS ("+FILTRADOS+") SELECT a.moneda,count(DISTINCT a.id),sum(l.debito),sum(l.credito)"
                +" FROM seleccion a "+MOVIMIENTOS+" GROUP BY a.moneda ORDER BY a.moneda",
            parametros(p,f),(r,n)->new TotalMoneda(r.getString(1),r.getLong(2),new Total(r.getBigDecimal(3),r.getBigDecimal(4))));
    }

    private static Asiento cabecera(ResultSet r,UUID id)throws SQLException {
        return new Asiento(id,r.getString("numero"),r.getObject("fecha",LocalDate.class),Tipo.valueOf(r.getString("tipo")),
            Origen.valueOf(r.getString("origen")),Estado.valueOf(r.getString("estado")),r.getString("concepto"),
            r.getString("referencia"),r.getString("fuente_numero"),r.getString("moneda"),new ArrayList<>(),null);
    }

    private static MapSqlParameterSource parametros(TenantPrincipal p,Filtro f) {
        return new MapSqlParameterSource("tenant",p.tenantId()).addValue("empresa",p.empresaId())
            .addValue("desde",f.desde()).addValue("hasta",f.hasta()).addValue("tipo",nombre(f.tipo()))
            .addValue("origen",nombre(f.origen())).addValue("buscar",f.buscar());
    }

    private static String nombre(Enum<?> v) {return v==null?null:v.name();}
}
