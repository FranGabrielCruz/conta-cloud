package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.OrigenMovimientoInventario;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

@Repository
public class MovimientoInventarioConsultaRepository {
    private static final String ORIGIN = "CASE WHEN im.reference_type='PURCHASE_RECEIPT' THEN 'RECEPCION' "
        + "WHEN im.reference_type='PURCHASE_RECEIPT_VOID' THEN 'REVERSION' "
        + "WHEN im.reference_type LIKE '%TRANSFER%' THEN 'TRANSFERENCIA' "
        + "WHEN im.reference_type LIKE '%ADJUST%' THEN 'AJUSTE' "
        + "WHEN im.reference_type LIKE '%COUNT%' THEN 'CONTEO_FISICO' "
        + "WHEN im.reference_type LIKE '%RETURN%' THEN 'DEVOLUCION' "
        + "WHEN im.reference_type LIKE '%SALE%' OR im.reference_type LIKE '%INVOICE%' THEN 'VENTA' "
        + "ELSE 'INVENTARIO_INICIAL' END";

    private final NamedParameterJdbcTemplate jdbc;

    public MovimientoInventarioConsultaRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PaginaMovimientosInventarioDto general(UUID tenant, UUID empresa, boolean todasSucursales,
            Set<UUID> sucursalesPermitidas, String buscar, LocalDate desde, LocalDate hasta, UUID sucursal,
            UUID almacen, UUID producto, String direction, OrigenMovimientoInventario origin, Pageable pageable) {
        MapSqlParameterSource parameters = params(tenant, empresa, todasSucursales, sucursalesPermitidas,
            buscar, desde, hasta, sucursal, almacen, producto, direction, origin, pageable);
        ResumenMovimientosInventarioDto summary = generalSummary(parameters);
        List<MovimientoInventarioDto> rows = jdbc.query(generalSql(true), parameters, (result, index) -> row(result));
        return new PaginaMovimientosInventarioDto(new PageImpl<>(rows, pageable, summary.movimientos()), summary);
    }

    public MovimientosInventarioReporteConsultaDto generalCompleto(UUID tenant, UUID empresa,
            boolean todasSucursales, Set<UUID> sucursalesPermitidas, String buscar, LocalDate desde,
            LocalDate hasta, UUID sucursal, UUID almacen, UUID producto, String direction,
            OrigenMovimientoInventario origin) {
        MapSqlParameterSource parameters = params(tenant, empresa, todasSucursales, sucursalesPermitidas,
            buscar, desde, hasta, sucursal, almacen, producto, direction, origin, Pageable.ofSize(1));
        ResumenMovimientosInventarioDto summary = generalSummary(parameters);
        List<MovimientoInventarioDto> rows = jdbc.query(generalSql(false), parameters, (result, index) -> row(result));
        return new MovimientosInventarioReporteConsultaDto(rows, summary);
    }

    public PaginaKardexDto kardex(UUID tenant, UUID empresa, boolean todasSucursales,
            Set<UUID> sucursalesPermitidas, UUID producto, UUID sucursal, UUID almacen, LocalDate desde,
            LocalDate hasta, Pageable pageable) {
        MapSqlParameterSource parameters = params(tenant, empresa, todasSucursales, sucursalesPermitidas,
            "", desde, hasta, sucursal, almacen, producto, null, null, pageable);
        ResumenKardexDto summary = kardexSummary(parameters);
        long count = countKardex(parameters);
        List<MovimientoInventarioDto> rows = jdbc.query(kardexSql(true), parameters, (result, index) -> row(result));
        return new PaginaKardexDto(new PageImpl<>(rows, pageable, count), summary);
    }

    public KardexInventarioReporteConsultaDto kardexCompleto(UUID tenant, UUID empresa,
            boolean todasSucursales, Set<UUID> sucursalesPermitidas, UUID producto, UUID sucursal,
            UUID almacen, LocalDate desde, LocalDate hasta) {
        MapSqlParameterSource parameters = params(tenant, empresa, todasSucursales, sucursalesPermitidas,
            "", desde, hasta, sucursal, almacen, producto, null, null, Pageable.ofSize(1));
        ResumenKardexDto summary = kardexSummary(parameters);
        List<MovimientoInventarioDto> rows = jdbc.query(kardexSql(false), parameters, (result, index) -> row(result));
        return new KardexInventarioReporteConsultaDto(rows, summary);
    }

    public MovimientoInventarioDetalleDto detalle(UUID tenant, UUID empresa, boolean todasSucursales,
            Set<UUID> sucursalesPermitidas, UUID id) {
        MapSqlParameterSource parameters = baseParams(tenant, empresa, todasSucursales, sucursalesPermitidas)
            .addValue("id", id, Types.OTHER);
        String sql = cte(false)
            + "SELECT b.*,coalesce(u.nombre || ' ' || u.apellido,u.usuario) actor,"
            + "sup.trade_name supplier_name,rev.id reversed_by_id,rev.movement_number reversed_by_number,"
            + "orig.movement_number original_number FROM balanced b JOIN usuarios u ON u.id=b.created_by "
            + "LEFT JOIN inventory_movement rev ON rev.reversal_of_id=b.id "
            + "LEFT JOIN inventory_movement orig ON orig.id=b.reversal_of_id "
            + "LEFT JOIN purchase_receipt pr ON pr.id=b.reference_id "
            + "AND b.reference_type IN('PURCHASE_RECEIPT','PURCHASE_RECEIPT_VOID') "
            + "LEFT JOIN supplier sup ON sup.id=pr.supplier_id WHERE b.id=:id";
        List<MovimientoInventarioDetalleDto> rows = jdbc.query(sql, parameters, (result, index) -> detailRow(result));
        return rows.stream().findFirst().orElse(null);
    }

    private ResumenMovimientosInventarioDto generalSummary(MapSqlParameterSource parameters) {
        Map<String, Object> values = jdbc.queryForMap(cte(false)
            + "SELECT count(*) movimientos,count(DISTINCT product_id) productos " + where(), parameters);
        return new ResumenMovimientosInventarioDto(number(values.get("movimientos")).longValue(),
            number(values.get("productos")).longValue());
    }

    private ResumenKardexDto kardexSummary(MapSqlParameterSource parameters) {
        String scope = "FROM inventory_movement im JOIN warehouse w ON w.id=im.warehouse_id "
            + "WHERE im.tenant_id=:tenant AND im.empresa_id=:empresa AND im.product_id=:producto "
            + authorizedScope() + " AND (CAST(:sucursal AS uuid) IS NULL OR w.branch_id=:sucursal) "
            + "AND (CAST(:almacen AS uuid) IS NULL OR im.warehouse_id=:almacen)";
        String sql = "SELECT coalesce(sum(CASE WHEN (CAST(:desde AS timestamptz) IS NOT NULL "
            + "AND im.effective_at<:desde) THEN CASE WHEN im.direction='IN' THEN im.base_quantity "
            + "ELSE -im.base_quantity END ELSE 0 END),0) inicial,"
            + "coalesce(sum(CASE WHEN (CAST(:desde AS timestamptz) IS NULL OR im.effective_at>=:desde) "
            + "AND (CAST(:hasta AS timestamptz) IS NULL OR im.effective_at<:hasta) "
            + "AND im.direction='IN' THEN im.base_quantity ELSE 0 END),0) entradas,"
            + "coalesce(sum(CASE WHEN (CAST(:desde AS timestamptz) IS NULL OR im.effective_at>=:desde) "
            + "AND (CAST(:hasta AS timestamptz) IS NULL OR im.effective_at<:hasta) "
            + "AND im.direction='OUT' THEN im.base_quantity ELSE 0 END),0) salidas " + scope;
        Map<String, Object> values = jdbc.queryForMap(sql, parameters);
        BigDecimal initial = decimal(values.get("inicial"));
        BigDecimal entries = decimal(values.get("entradas"));
        BigDecimal exits = decimal(values.get("salidas"));
        return new ResumenKardexDto(initial, entries, exits, initial.add(entries).subtract(exits));
    }

    private long countKardex(MapSqlParameterSource parameters) {
        Long count = jdbc.queryForObject(cte(true) + "SELECT count(*) " + where(), parameters, Long.class);
        return count == null ? 0 : count;
    }

    static String generalSql(boolean paginated) {
        return cte(false) + "SELECT * " + where()
            + "ORDER BY effective_at DESC,created_at DESC,movement_number DESC,id DESC"
            + (paginated ? " LIMIT :limit OFFSET :offset" : "");
    }

    static String kardexSql(boolean paginated) {
        return cte(true) + "SELECT * " + where()
            + "ORDER BY effective_at,created_at,movement_number,id"
            + (paginated ? " LIMIT :limit OFFSET :offset" : "");
    }

    static String cte(boolean scoped) {
        return "WITH balanced AS (SELECT im.*,w.branch_id branch_id,p.code product_code,"
            + "p.name product_name,uom.name unit_name,w.name warehouse_name,s.nombre branch_name,"
            + ORIGIN + " origin,sum(CASE WHEN im.direction='IN' THEN im.base_quantity "
            + "ELSE -im.base_quantity END) OVER(PARTITION BY "
            + (scoped ? "im.product_id" : "im.warehouse_id,im.product_id")
            + " ORDER BY im.effective_at,im.created_at,im.movement_number,im.id "
            + "ROWS UNBOUNDED PRECEDING) balance_after,EXISTS(SELECT 1 FROM inventory_movement x "
            + "WHERE x.reversal_of_id=im.id) reversed FROM inventory_movement im "
            + "JOIN product p ON p.id=im.product_id "
            + "JOIN unit_of_measure uom ON uom.id=im.unit_of_measure_id "
            + "JOIN warehouse w ON w.id=im.warehouse_id JOIN sucursales s ON s.id=w.branch_id "
            + "WHERE im.tenant_id=:tenant AND im.empresa_id=:empresa " + authorizedScope()
            + (scoped ? " AND im.product_id=:producto "
                + "AND (CAST(:sucursal AS uuid) IS NULL OR w.branch_id=:sucursal) "
                + "AND (CAST(:almacen AS uuid) IS NULL OR im.warehouse_id=:almacen)" : "")
            + ") ";
    }

    private static String authorizedScope() {
        return "AND (:todasSucursales=true OR w.branch_id IN (:sucursalesPermitidas))";
    }

    private static String where() {
        return "FROM balanced b WHERE (:buscar='' OR lower(b.product_name) LIKE :buscar "
            + "OR lower(b.product_code) LIKE :buscar OR lower(b.origin_document_number) LIKE :buscar "
            + "OR lower(b.movement_number) LIKE :buscar OR lower(coalesce(b.origin_reference,'')) LIKE :buscar) "
            + "AND (CAST(:desde AS timestamptz) IS NULL OR b.effective_at>=:desde) "
            + "AND (CAST(:hasta AS timestamptz) IS NULL OR b.effective_at<:hasta) "
            + "AND (CAST(:sucursal AS uuid) IS NULL OR b.branch_id=:sucursal) "
            + "AND (CAST(:almacen AS uuid) IS NULL OR b.warehouse_id=:almacen) "
            + "AND (CAST(:producto AS uuid) IS NULL OR b.product_id=:producto) "
            + "AND (CAST(:direction AS varchar) IS NULL OR b.direction=:direction) "
            + "AND (CAST(:origin AS varchar) IS NULL OR b.origin=:origin) ";
    }

    private static MapSqlParameterSource params(UUID tenant, UUID empresa, boolean todasSucursales,
            Set<UUID> sucursalesPermitidas, String buscar, LocalDate desde, LocalDate hasta, UUID sucursal,
            UUID almacen, UUID producto, String direction, OrigenMovimientoInventario origin, Pageable pageable) {
        return baseParams(tenant, empresa, todasSucursales, sucursalesPermitidas)
            .addValue("buscar", buscar == null || buscar.isBlank() ? "" : "%" + buscar.trim().toLowerCase(Locale.ROOT) + "%", Types.VARCHAR)
            .addValue("desde", desde == null ? null : desde.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime(), Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("hasta", hasta == null ? null : hasta.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime(), Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("sucursal", sucursal, Types.OTHER).addValue("almacen", almacen, Types.OTHER)
            .addValue("producto", producto, Types.OTHER).addValue("direction", direction, Types.VARCHAR)
            .addValue("origin", origin == null ? null : origin.name(), Types.VARCHAR)
            .addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
    }

    private static MapSqlParameterSource baseParams(UUID tenant, UUID empresa, boolean todasSucursales,
            Set<UUID> sucursalesPermitidas) {
        Set<UUID> allowed = sucursalesPermitidas == null || sucursalesPermitidas.isEmpty()
            ? Set.of(new UUID(0L, 0L)) : Set.copyOf(sucursalesPermitidas);
        return new MapSqlParameterSource().addValue("tenant", tenant, Types.OTHER)
            .addValue("empresa", empresa, Types.OTHER).addValue("todasSucursales", todasSucursales, Types.BOOLEAN)
            .addValue("sucursalesPermitidas", allowed);
    }

    private static MovimientoInventarioDto row(ResultSet result) throws SQLException {
        boolean incoming = "IN".equals(result.getString("direction"));
        BigDecimal quantity = result.getBigDecimal("base_quantity");
        return new MovimientoInventarioDto(result.getObject("id", UUID.class), result.getString("movement_number"),
            result.getObject("effective_at", OffsetDateTime.class), result.getString("origin_document_number"),
            result.getObject("reference_id", UUID.class), result.getString("origin_reference"),
            result.getObject("product_id", UUID.class), result.getString("product_code"),
            result.getString("product_name"), result.getString("unit_name"),
            result.getObject("warehouse_id", UUID.class), result.getString("warehouse_name"),
            result.getString("branch_name"), incoming ? "Entrada" : "Salida", origin(result.getString("origin")),
            incoming ? quantity : BigDecimal.ZERO, incoming ? BigDecimal.ZERO : quantity,
            result.getBigDecimal("balance_after"), result.getBoolean("reversed") ? "REVERTIDO"
                : ("REVERSION".equals(result.getString("origin")) ? "REVERSIÓN" : "VIGENTE"));
    }

    private static MovimientoInventarioDetalleDto detailRow(ResultSet result) throws SQLException {
        BigDecimal quantity = result.getBigDecimal("base_quantity");
        BigDecimal after = result.getBigDecimal("balance_after");
        boolean incoming = "IN".equals(result.getString("direction"));
        return new MovimientoInventarioDetalleDto(result.getObject("id", UUID.class), result.getString("movement_number"),
            result.getObject("effective_at", OffsetDateTime.class), result.getObject("created_at", OffsetDateTime.class),
            incoming ? "Entrada" : "Salida", origin(result.getString("origin")),
            result.getString("origin_document_number"), result.getObject("reference_id", UUID.class),
            result.getString("origin_reference"), result.getObject("reversed_by_id") == null ? "VIGENTE" : "REVERTIDO",
            result.getString("product_name"), result.getString("product_code"), result.getString("unit_name"),
            result.getString("warehouse_name"), result.getString("branch_name"),
            incoming ? after.subtract(quantity) : after.add(quantity), incoming ? quantity : BigDecimal.ZERO,
            incoming ? BigDecimal.ZERO : quantity, after, result.getBigDecimal("source_quantity"),
            result.getBigDecimal("conversion_factor"), quantity, result.getString("actor"),
            result.getString("supplier_name"), result.getObject("reversal_of_id", UUID.class),
            result.getString("original_number"), result.getObject("reversed_by_id", UUID.class),
            result.getString("reversed_by_number"), result.getString("reference_type"));
    }

    private static Number number(Object value) {
        return value instanceof Number number ? number : 0L;
    }

    private static BigDecimal decimal(Object value) {
        return value instanceof BigDecimal decimal ? decimal : BigDecimal.ZERO;
    }

    private static OrigenMovimientoInventario origin(String value) {
        return OrigenMovimientoInventario.valueOf(value);
    }
}
