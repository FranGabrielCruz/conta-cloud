package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.dto.ExistenciaInventarioDto;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.*;

@Repository
public class ExistenciaInventarioConsultaRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public ExistenciaInventarioConsultaRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}

    public Page<ExistenciaInventarioDto> buscar(UUID tenant, UUID empresa, boolean todasSucursales,
            Set<UUID> sucursalesPermitidas, String buscar, UUID sucursal, UUID almacen, UUID categoria,
            Boolean activo, String stock, Pageable pageable) {
        var params=params(tenant,empresa,todasSucursales,sucursalesPermitidas,buscar,sucursal,almacen,categoria,activo,stock);
        long total=Optional.ofNullable(jdbc.queryForObject("SELECT count(*) FROM ("+baseSql()+") q",params,Long.class)).orElse(0L);
        params.addValue("limit",pageable.getPageSize()).addValue("offset",pageable.getOffset());
        String order=order(pageable.getSort());
        List<ExistenciaInventarioDto> rows=jdbc.query(baseSql()+" ORDER BY "+order+" LIMIT :limit OFFSET :offset",params,(rs,i)->row(rs));
        return new PageImpl<>(rows,pageable,total);
    }

    public List<ExistenciaInventarioDto> buscarCompleto(UUID tenant, UUID empresa, boolean todasSucursales,
            Set<UUID> sucursalesPermitidas, String buscar, UUID sucursal, UUID almacen, UUID categoria,
            Boolean activo, String stock) {
        var params=params(tenant,empresa,todasSucursales,sucursalesPermitidas,buscar,sucursal,almacen,categoria,activo,stock);
        return jdbc.query(baseSql()+" ORDER BY producto,almacen",params,(rs,i)->row(rs));
    }

    public List<ExistenciaInventarioDto> distribucion(UUID tenant,UUID empresa,boolean todasSucursales,
            Set<UUID> sucursalesPermitidas,UUID producto) {
        var params=params(tenant,empresa,todasSucursales,sucursalesPermitidas,"",null,null,null,null,"TODOS");
        params.addValue("producto",producto);
        return jdbc.query(baseSql()+" AND p.id=:producto ORDER BY almacen",params,(rs,i)->row(rs));
    }

    private static String baseSql(){return """
        WITH saldos AS (
          SELECT tenant_id,empresa_id,product_id,warehouse_id,
                 sum(CASE WHEN direction='IN' THEN base_quantity ELSE -base_quantity END) existencia
          FROM inventory_movement WHERE tenant_id=:tenant AND empresa_id=:empresa
          GROUP BY tenant_id,empresa_id,product_id,warehouse_id
        )
        SELECT p.id producto_id,w.id almacen_id,w.branch_id sucursal_id,p.name producto,p.code codigo,
               coalesce(u.abbreviation,u.name,'—') unidad,coalesce(c.name,'Sin categoría') categoria,
               b.nombre sucursal,w.name almacen,p.active producto_activo,w.active almacen_activo,
               coalesce(s.existencia,0) existencia
        FROM product p CROSS JOIN warehouse w
        JOIN sucursales b ON b.id=w.branch_id AND b.empresa_id=w.empresa_id
        JOIN unit_of_measure u ON u.id=p.unit_of_measure_id AND u.tenant_id=p.tenant_id AND u.empresa_id=p.empresa_id
        LEFT JOIN product_category c ON c.id=p.category_id AND c.tenant_id=p.tenant_id AND c.empresa_id=p.empresa_id
        LEFT JOIN saldos s ON s.product_id=p.id AND s.warehouse_id=w.id AND s.tenant_id=p.tenant_id AND s.empresa_id=p.empresa_id
        WHERE p.tenant_id=:tenant AND p.empresa_id=:empresa AND w.tenant_id=:tenant AND w.empresa_id=:empresa
          AND p.product_type='PRODUCT' AND p.track_inventory
          AND (:todas_sucursales OR w.branch_id IN (:sucursales_permitidas))
          AND (:buscar='' OR lower(p.name) LIKE concat('%',:buscar,'%') OR lower(p.code) LIKE concat('%',:buscar,'%')
               OR lower(coalesce(p.barcode,'')) LIKE concat('%',:buscar,'%'))
          AND (CAST(:sucursal AS uuid) IS NULL OR w.branch_id=CAST(:sucursal AS uuid))
          AND (CAST(:almacen AS uuid) IS NULL OR w.id=CAST(:almacen AS uuid))
          AND (CAST(:categoria AS uuid) IS NULL OR p.category_id=CAST(:categoria AS uuid))
          AND (CAST(:activo AS boolean) IS NULL OR p.active=CAST(:activo AS boolean))
          AND (:stock='TODOS' OR (:stock='CON_EXISTENCIA' AND coalesce(s.existencia,0)>0)
               OR (:stock='SIN_EXISTENCIA' AND coalesce(s.existencia,0)=0)
               OR (:stock='NEGATIVA' AND coalesce(s.existencia,0)<0))
        """;}

    private static MapSqlParameterSource params(UUID tenant,UUID empresa,boolean todas,Set<UUID> permitidas,
            String buscar,UUID sucursal,UUID almacen,UUID categoria,Boolean activo,String stock){
        return new MapSqlParameterSource().addValue("tenant",tenant).addValue("empresa",empresa)
            .addValue("todas_sucursales",todas).addValue("sucursales_permitidas",permitidas.isEmpty()?Set.of(new UUID(0,0)):permitidas)
            .addValue("buscar",buscar==null?"":buscar.trim().toLowerCase(Locale.ROOT)).addValue("sucursal",sucursal,Types.OTHER)
            .addValue("almacen",almacen,Types.OTHER).addValue("categoria",categoria,Types.OTHER).addValue("activo",activo,Types.BOOLEAN)
            .addValue("stock",stock==null?"TODOS":stock);
    }
    private static ExistenciaInventarioDto row(ResultSet rs)throws SQLException{return new ExistenciaInventarioDto(
        rs.getObject("producto_id",UUID.class),rs.getObject("almacen_id",UUID.class),rs.getObject("sucursal_id",UUID.class),
        rs.getString("producto"),rs.getString("codigo"),rs.getString("unidad"),rs.getString("categoria"),
        rs.getString("sucursal"),rs.getString("almacen"),rs.getBoolean("producto_activo"),rs.getBoolean("almacen_activo"),
        Optional.ofNullable(rs.getBigDecimal("existencia")).orElse(BigDecimal.ZERO));}
    private static String order(Sort sort){
        if(sort.isUnsorted())return "producto ASC,almacen ASC";
        var parts=new ArrayList<String>();for(Sort.Order o:sort){String column=switch(o.getProperty()){
            case "codigo"->"codigo";case "almacen"->"almacen";case "existencia"->"existencia";default->"producto";};
            parts.add(column+(o.isDescending()?" DESC":" ASC"));}return String.join(",",parts);
    }
}
