package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.dto.*;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

@Repository
public class AlmacenConsultaRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public AlmacenConsultaRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}

    public long contarProductosConExistencia(UUID tenant,UUID empresa,UUID almacen){
        return value("""
            SELECT count(*) FROM (
              SELECT im.product_id FROM inventory_movement im
              WHERE im.tenant_id=:tenant AND im.empresa_id=:empresa AND im.warehouse_id=:almacen
              GROUP BY im.product_id
              HAVING sum(CASE WHEN im.direction='IN' THEN im.quantity ELSE -im.quantity END)<>0
            ) x
            """,params(tenant,empresa,almacen),Long.class,0L);
    }

    public Map<UUID,Long> contarProductosConExistencia(UUID tenant,UUID empresa,Collection<UUID> almacenes){
        if(almacenes.isEmpty())return Map.of();
        MapSqlParameterSource p=new MapSqlParameterSource().addValue("tenant",tenant).addValue("empresa",empresa).addValue("almacenes",almacenes);
        Map<UUID,Long> result=new HashMap<>();
        jdbc.query("""
            SELECT x.warehouse_id,count(*) total FROM (
              SELECT warehouse_id,product_id FROM inventory_movement
              WHERE tenant_id=:tenant AND empresa_id=:empresa AND warehouse_id IN (:almacenes)
              GROUP BY warehouse_id,product_id
              HAVING sum(CASE WHEN direction='IN' THEN quantity ELSE -quantity END)<>0
            ) x GROUP BY x.warehouse_id
            """,p,rs->{result.put(rs.getObject("warehouse_id",UUID.class),rs.getLong("total"));});
        return result;
    }

    public boolean tieneExistencias(UUID tenant,UUID empresa,UUID almacen){
        return contarProductosConExistencia(tenant,empresa,almacen)>0;
    }

    public boolean tieneHistorial(UUID tenant,UUID empresa,UUID almacen){
        return value("""
            SELECT EXISTS(
              SELECT 1 FROM inventory_movement WHERE tenant_id=:tenant AND empresa_id=:empresa AND warehouse_id=:almacen
              UNION ALL
              SELECT 1 FROM purchase_receipt WHERE tenant_id=:tenant AND empresa_id=:empresa AND warehouse_id=:almacen
            )
            """,params(tenant,empresa,almacen),Boolean.class,false);
    }

    public boolean tieneRecepcionesPendientes(UUID tenant,UUID empresa,UUID almacen){
        return value("""
            SELECT EXISTS(SELECT 1 FROM purchase_receipt
              WHERE tenant_id=:tenant AND empresa_id=:empresa AND warehouse_id=:almacen AND status='DRAFT')
            """,params(tenant,empresa,almacen),Boolean.class,false);
    }

    public Page<ExistenciaAlmacenDto> existencias(UUID tenant,UUID empresa,UUID almacen,String buscar,
                                                  UUID categoria,String estado,Pageable pageable){
        MapSqlParameterSource p=params(tenant,empresa,almacen).addValue("buscar",like(buscar))
            .addValue("categoria",categoria).addValue("estado",estado).addValue("limit",pageable.getPageSize())
            .addValue("offset",pageable.getOffset());
        String base="""
            FROM product pr
            JOIN unit_of_measure u ON u.id=pr.unit_of_measure_id
            LEFT JOIN product_category c ON c.id=pr.category_id
            LEFT JOIN (
              SELECT product_id,sum(CASE WHEN direction='IN' THEN quantity ELSE -quantity END) AS stock
              FROM inventory_movement
              WHERE tenant_id=:tenant AND empresa_id=:empresa AND warehouse_id=:almacen
              GROUP BY product_id
            ) s ON s.product_id=pr.id
            WHERE pr.tenant_id=:tenant AND pr.empresa_id=:empresa AND pr.track_inventory
              AND (:buscar='' OR lower(pr.code) LIKE :buscar OR lower(pr.name) LIKE :buscar)
              AND (:categoria IS NULL OR pr.category_id=:categoria)
              AND (:estado='TODOS' OR (:estado='CON_EXISTENCIA' AND coalesce(s.stock,0)<>0)
                   OR (:estado='SIN_EXISTENCIA' AND coalesce(s.stock,0)=0))
            """;
        long total=value("SELECT count(*) "+base,p,Long.class,0L);
        List<ExistenciaAlmacenDto> rows=jdbc.query("""
            SELECT pr.id,pr.code,pr.name,coalesce(c.name,'Sin categoría') categoria,u.abbreviation,
                   coalesce(s.stock,0) existencia
            """+base+" ORDER BY pr.name,pr.id LIMIT :limit OFFSET :offset",p,(rs,n)->{
                BigDecimal stock=rs.getBigDecimal("existencia");
                return new ExistenciaAlmacenDto(rs.getObject("id",UUID.class),rs.getString("code"),rs.getString("name"),
                    rs.getString("categoria"),rs.getString("abbreviation"),stock,BigDecimal.ZERO,stock);
            });
        return new PageImpl<>(rows,pageable,total);
    }

    public Page<MovimientoAlmacenDto> movimientos(UUID tenant,UUID empresa,UUID almacen,String buscar,
                                                  String tipo,LocalDate desde,LocalDate hasta,Pageable pageable){
        MapSqlParameterSource p=params(tenant,empresa,almacen).addValue("buscar",like(buscar)).addValue("tipo",tipo)
            .addValue("desde",desde==null?null:Date.valueOf(desde)).addValue("hasta",hasta==null?null:Date.valueOf(hasta))
            .addValue("limit",pageable.getPageSize()).addValue("offset",pageable.getOffset());
        String cte="""
            WITH balanced AS (
              SELECT im.*,pr.code product_code,pr.name product_name,rc.receipt_number,
                sum(CASE WHEN im.direction='IN' THEN im.quantity ELSE -im.quantity END)
                  OVER(PARTITION BY im.product_id ORDER BY im.movement_date,im.created_at,im.id ROWS UNBOUNDED PRECEDING) balance_after
              FROM inventory_movement im JOIN product pr ON pr.id=im.product_id
              LEFT JOIN purchase_receipt rc ON rc.id=im.reference_id
                AND im.reference_type IN ('PURCHASE_RECEIPT','PURCHASE_RECEIPT_VOID')
              WHERE im.tenant_id=:tenant AND im.empresa_id=:empresa AND im.warehouse_id=:almacen
            )
            """;
        String where="""
            FROM balanced b WHERE
              (:buscar='' OR lower(b.product_code) LIKE :buscar OR lower(b.product_name) LIKE :buscar
                 OR lower(b.reference_type) LIKE :buscar OR CAST(b.reference_id AS text) LIKE :buscar)
              AND (:tipo IS NULL OR b.movement_type=:tipo)
              AND (:desde IS NULL OR b.movement_date>=:desde) AND (:hasta IS NULL OR b.movement_date<=:hasta)
            """;
        long total=value(cte+"SELECT count(*) "+where,p,Long.class,0L);
        List<MovimientoAlmacenDto> rows=jdbc.query(cte+"""
            SELECT b.id,b.movement_date,b.product_name,b.movement_type,b.reference_type,b.reference_id,b.direction,b.quantity,b.balance_after,b.receipt_number
            """+where+" ORDER BY b.movement_date DESC,b.created_at DESC,b.id DESC LIMIT :limit OFFSET :offset",p,(rs,n)->{
                BigDecimal q=rs.getBigDecimal("quantity");boolean in="IN".equals(rs.getString("direction"));
                String reference=rs.getString("receipt_number");
                if(reference==null)reference=rs.getString("reference_type")+" · "+rs.getObject("reference_id",UUID.class).toString().substring(0,8);
                return new MovimientoAlmacenDto(rs.getObject("id",UUID.class),rs.getDate("movement_date").toLocalDate(),reference,
                    rs.getString("product_name"),etiqueta(rs.getString("movement_type")),in?q:BigDecimal.ZERO,in?BigDecimal.ZERO:q,
                    rs.getBigDecimal("balance_after"),rs.getString("reference_type"),rs.getObject("reference_id",UUID.class));
            });
        return new PageImpl<>(rows,pageable,total);
    }

    public List<String> tiposMovimiento(UUID tenant,UUID empresa,UUID almacen){
        return jdbc.queryForList("""
            SELECT DISTINCT movement_type FROM inventory_movement
            WHERE tenant_id=:tenant AND empresa_id=:empresa AND warehouse_id=:almacen ORDER BY movement_type
            """,params(tenant,empresa,almacen),String.class);
    }

    private static MapSqlParameterSource params(UUID tenant,UUID empresa,UUID almacen){return new MapSqlParameterSource()
        .addValue("tenant",tenant).addValue("empresa",empresa).addValue("almacen",almacen);}
    private static String like(String value){return value==null||value.isBlank()?"":"%"+value.trim().toLowerCase(Locale.ROOT)+"%";}
    private static String etiqueta(String value){return value==null?"Movimiento":value.replace('_',' ').toLowerCase(Locale.ROOT);}
    private <T>T value(String sql,MapSqlParameterSource p,Class<T> type,T fallback){T value=jdbc.queryForObject(sql,p,type);return value==null?fallback:value;}
}
