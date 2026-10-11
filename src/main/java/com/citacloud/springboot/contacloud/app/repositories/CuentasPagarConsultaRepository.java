package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.dto.*;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Types;
import java.time.*;
import java.util.*;

@Repository
public class CuentasPagarConsultaRepository {
    private static final UUID EMPTY_ID=new UUID(0,0);
    private final NamedParameterJdbcTemplate jdbc;

    public CuentasPagarConsultaRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}

    public Page<CuentaPagarFacturaDto> facturas(Filtros f,Pageable page){
        String sql=BASE+" SELECT * FROM deuda d WHERE "+WHERE+
            " ORDER BY d.vencimiento,d.factura LIMIT :limit OFFSET :offset";
        String count=BASE+" SELECT count(*) FROM deuda d WHERE "+WHERE;
        var p=params(f,page);List<CuentaPagarFacturaDto> rows=jdbc.query(sql,p,(rs,n)->new CuentaPagarFacturaDto(
            rs.getObject("factura_id",UUID.class),rs.getObject("proveedor_id",UUID.class),rs.getString("factura"),
            rs.getString("proveedor"),rs.getString("identificacion"),rs.getString("numero_proveedor"),
            rs.getString("numero_fiscal"),rs.getObject("fecha",LocalDate.class),rs.getObject("vencimiento",LocalDate.class),
            rs.getObject("moneda_id",UUID.class),rs.getString("moneda"),rs.getString("simbolo"),
            rs.getBigDecimal("total"),rs.getBigDecimal("aplicado"),rs.getBigDecimal("saldo"),
            estado(rs.getBigDecimal("aplicado"),rs.getBigDecimal("saldo")),
            situacion(rs.getObject("vencimiento",LocalDate.class),f.al()),
            Math.abs(Duration.between(rs.getObject("vencimiento",LocalDate.class).atStartOfDay(),f.al().atStartOfDay()).toDays())));
        Long total=jdbc.queryForObject(count,p,Long.class);return new PageImpl<>(rows,page,total==null?0:total);
    }

    public Page<CuentaPagarProveedorDto> proveedores(Filtros f,Pageable page){
        String select="""
          SELECT d.proveedor_id,max(d.proveedor) proveedor,max(d.identificacion) identificacion,
                 d.moneda_id,max(d.moneda) moneda,max(d.simbolo) simbolo,
                 sum(CASE WHEN d.vencimiento>:al THEN d.saldo ELSE 0 END) por_vencer,
                 sum(CASE WHEN d.vencimiento=:al THEN d.saldo ELSE 0 END) vence_hoy,
                 sum(CASE WHEN d.vencimiento<:al THEN d.saldo ELSE 0 END) vencido,sum(d.saldo) pendiente,
                 coalesce(max(cr.disponible),0) credito
          FROM deuda d LEFT JOIN creditos cr ON cr.proveedor_id=d.proveedor_id AND cr.moneda_id=d.moneda_id
          WHERE %s GROUP BY d.proveedor_id,d.moneda_id ORDER BY pendiente DESC,max(d.proveedor)
          """.formatted(WHERE);
        String sql=BASE+CREDITS+select+" LIMIT :limit OFFSET :offset";
        String count=BASE+" SELECT count(*) FROM (SELECT d.proveedor_id,d.moneda_id FROM deuda d WHERE "+WHERE+
            " GROUP BY d.proveedor_id,d.moneda_id) x";
        var p=params(f,page);List<CuentaPagarProveedorDto> rows=jdbc.query(sql,p,(rs,n)->new CuentaPagarProveedorDto(
            rs.getObject("proveedor_id",UUID.class),rs.getString("proveedor"),rs.getString("identificacion"),
            rs.getObject("moneda_id",UUID.class),rs.getString("moneda"),rs.getString("simbolo"),
            rs.getBigDecimal("por_vencer"),rs.getBigDecimal("vence_hoy"),rs.getBigDecimal("vencido"),
            rs.getBigDecimal("pendiente"),rs.getBigDecimal("credito")));
        Long total=jdbc.queryForObject(count,p,Long.class);return new PageImpl<>(rows,page,total==null?0:total);
    }

    public Page<AntiguedadCuentaPagarDto> antiguedad(Filtros f,Pageable page){
        String grouped="""
          SELECT d.proveedor_id,max(d.proveedor) proveedor,d.moneda_id,max(d.moneda) moneda,max(d.simbolo) simbolo,
            sum(CASE WHEN d.vencimiento>:al THEN d.saldo ELSE 0 END) por_vencer,
            sum(CASE WHEN d.vencimiento=:al THEN d.saldo ELSE 0 END) vence_hoy,
            sum(CASE WHEN d.vencimiento<:al AND d.vencimiento>=(CAST(:al AS date)-30) THEN d.saldo ELSE 0 END) d1_30,
            sum(CASE WHEN d.vencimiento<(CAST(:al AS date)-30) AND d.vencimiento>=(CAST(:al AS date)-60) THEN d.saldo ELSE 0 END) d31_60,
            sum(CASE WHEN d.vencimiento<(CAST(:al AS date)-60) AND d.vencimiento>=(CAST(:al AS date)-90) THEN d.saldo ELSE 0 END) d61_90,
            sum(CASE WHEN d.vencimiento<(CAST(:al AS date)-90) THEN d.saldo ELSE 0 END) mas90,sum(d.saldo) total
          FROM deuda d WHERE %s GROUP BY d.proveedor_id,d.moneda_id ORDER BY total DESC,max(d.proveedor)
          """.formatted(WHERE);
        String sql=BASE+grouped+" LIMIT :limit OFFSET :offset";
        String count=BASE+" SELECT count(*) FROM (SELECT d.proveedor_id,d.moneda_id FROM deuda d WHERE "+WHERE+
            " GROUP BY d.proveedor_id,d.moneda_id) x";
        var p=params(f,page);List<AntiguedadCuentaPagarDto> rows=jdbc.query(sql,p,(rs,n)->new AntiguedadCuentaPagarDto(
            rs.getObject("proveedor_id",UUID.class),rs.getString("proveedor"),rs.getObject("moneda_id",UUID.class),
            rs.getString("moneda"),rs.getString("simbolo"),rs.getBigDecimal("por_vencer"),rs.getBigDecimal("vence_hoy"),
            rs.getBigDecimal("d1_30"),rs.getBigDecimal("d31_60"),rs.getBigDecimal("d61_90"),rs.getBigDecimal("mas90"),rs.getBigDecimal("total")));
        Long total=jdbc.queryForObject(count,p,Long.class);return new PageImpl<>(rows,page,total==null?0:total);
    }

    public List<TotalesMoneda> totales(Filtros f){
        String sql=BASE+CREDITS+"""
          SELECT m.id moneda_id,m.codigo_iso moneda,m.simbolo,
                 coalesce(sum(CASE WHEN d.saldo>0 THEN d.saldo ELSE 0 END),0) pendiente,
                 coalesce(sum(CASE WHEN d.saldo>0 AND d.vencimiento<:al THEN d.saldo ELSE 0 END),0) vencido,
                 coalesce(sum(CASE WHEN d.saldo>0 AND d.vencimiento=:al THEN d.saldo ELSE 0 END),0) vence_hoy,
                 coalesce(sum(CASE WHEN d.saldo>0 AND d.vencimiento>:al THEN d.saldo ELSE 0 END),0) por_vencer,
                 coalesce((SELECT sum(c.disponible) FROM creditos c WHERE c.moneda_id=m.id),0) creditos
          FROM monedas m LEFT JOIN deuda d ON d.moneda_id=m.id AND ("""+WHERE+"""
          ) WHERE m.empresa_id=:empresa GROUP BY m.id,m.codigo_iso,m.simbolo
          HAVING coalesce(sum(CASE WHEN d.saldo>0 THEN d.saldo ELSE 0 END),0)>0
              OR coalesce((SELECT sum(c.disponible) FROM creditos c WHERE c.moneda_id=m.id),0)>0
          ORDER BY m.codigo_iso
          """;
        return jdbc.query(sql,params(f,null),(rs,n)->new TotalesMoneda(rs.getObject("moneda_id",UUID.class),
            rs.getString("moneda"),rs.getString("simbolo"),rs.getBigDecimal("pendiente"),rs.getBigDecimal("vencido"),
            rs.getBigDecimal("vence_hoy"),rs.getBigDecimal("por_vencer"),rs.getBigDecimal("creditos")));
    }

    public List<MovimientoCuentaPagarDto> movimientos(UUID tenant,UUID empresa,UUID factura,LocalDate al){
        String sql="""
          WITH movimientos AS (
            SELECT f.registered_at fecha,'Factura' tipo,f.internal_number documento,f.total cargo,0::numeric aplicacion,f.id documento_id,0 orden
            FROM purchase_invoice f WHERE f.tenant_id=:tenant AND f.empresa_id=:empresa AND f.id=:factura AND f.registered_at<:corte
            UNION ALL
            SELECT a.applied_at,'Pago',p.internal_number,0,a.amount,p.id,1 FROM supplier_payment_application a
              JOIN supplier_payment p ON p.id=a.supplier_payment_id
            WHERE a.tenant_id=:tenant AND a.empresa_id=:empresa AND a.purchase_invoice_id=:factura
              AND a.applied_at<:corte AND (a.reversed=false OR a.reversed_at>=:corte)
              AND p.confirmed_at<:corte AND (p.voided_at IS NULL OR p.voided_at>=:corte)
            UNION ALL
            SELECT a.applied_at,'Nota de crédito',n.internal_number,0,a.amount,n.id,2 FROM supplier_credit_application a
              JOIN supplier_credit_note n ON n.id=a.supplier_credit_note_id
            WHERE a.tenant_id=:tenant AND a.empresa_id=:empresa AND a.purchase_invoice_id=:factura
              AND a.applied_at<:corte AND (a.reversed=false OR a.reversed_at>=:corte)
              AND n.confirmed_at<:corte AND (n.voided_at IS NULL OR n.voided_at>=:corte))
          SELECT fecha,tipo,documento,cargo,aplicacion,documento_id,
                 sum(cargo-aplicacion) OVER(ORDER BY fecha,orden,documento ROWS UNBOUNDED PRECEDING) saldo
          FROM movimientos ORDER BY fecha,orden,documento
          """;
        var p=new MapSqlParameterSource().addValue("tenant",tenant).addValue("empresa",empresa)
            .addValue("factura",factura).addValue("corte",al.plusDays(1).atStartOfDay());
        return jdbc.query(sql,p,(rs,n)->new MovimientoCuentaPagarDto(rs.getObject("fecha",OffsetDateTime.class),
            rs.getString("tipo"),rs.getString("documento"),rs.getBigDecimal("cargo"),rs.getBigDecimal("aplicacion"),
            rs.getBigDecimal("saldo"),rs.getObject("documento_id",UUID.class)));
    }

    MapSqlParameterSource params(Filtros f,Pageable page){
        MapSqlParameterSource p=new MapSqlParameterSource().addValue("tenant",f.tenant(),Types.OTHER).addValue("empresa",f.empresa(),Types.OTHER)
            .addValue("al",Date.valueOf(f.al())).addValue("corte",f.al().plusDays(1).atStartOfDay())
            .addValue("buscar",f.buscar()).addValue("proveedor",f.proveedor(),Types.OTHER).addValue("moneda",f.moneda(),Types.OTHER)
            .addValue("sucursal",f.sucursal(),Types.OTHER).addValue("todasSucursales",f.todasSucursales())
            .addValue("sucursales",f.sucursales().isEmpty()?Set.of(EMPTY_ID):f.sucursales())
            .addValue("estado",f.estado()).addValue("situacion",f.situacion())
            .addValue("venceDesde",f.venceDesde()==null?null:Date.valueOf(f.venceDesde()),Types.DATE)
            .addValue("venceHasta",f.venceHasta()==null?null:Date.valueOf(f.venceHasta()),Types.DATE);
        if(page!=null)p.addValue("limit",page.getPageSize()).addValue("offset",page.getOffset());return p;
    }

    static String estado(BigDecimal aplicado,BigDecimal saldo){return saldo.signum()==0?"PAGADA":aplicado.signum()>0?"PARCIALMENTE PAGADA":"PENDIENTE";}
    static String situacion(LocalDate vence,LocalDate al){long days=java.time.temporal.ChronoUnit.DAYS.between(vence,al);if(days>0)return "Vencida · "+days+(days==1?" día":" días");if(days==0)return "Vence hoy";long future=-days;return "Vence en "+future+(future==1?" día":" días");}

    public record Filtros(UUID tenant,UUID empresa,LocalDate al,String buscar,UUID proveedor,UUID moneda,
            UUID sucursal,boolean todasSucursales,Set<UUID> sucursales,String estado,String situacion,
            LocalDate venceDesde,LocalDate venceHasta) {}
    public record TotalesMoneda(UUID monedaId,String moneda,String simbolo,BigDecimal pendiente,
            BigDecimal vencido,BigDecimal venceHoy,BigDecimal porVencer,BigDecimal creditos) {}

    private static final String BASE="""
      WITH pagos AS (
        SELECT a.purchase_invoice_id,sum(a.amount) aplicado FROM supplier_payment_application a
        JOIN supplier_payment p ON p.id=a.supplier_payment_id
        WHERE a.tenant_id=:tenant AND a.empresa_id=:empresa AND a.applied_at<:corte
          AND (a.reversed=false OR a.reversed_at>=:corte) AND p.confirmed_at<:corte
          AND (p.voided_at IS NULL OR p.voided_at>=:corte) GROUP BY a.purchase_invoice_id),
      notas AS (
        SELECT a.purchase_invoice_id,sum(a.amount) aplicado FROM supplier_credit_application a
        JOIN supplier_credit_note n ON n.id=a.supplier_credit_note_id
        WHERE a.tenant_id=:tenant AND a.empresa_id=:empresa AND a.applied_at<:corte
          AND (a.reversed=false OR a.reversed_at>=:corte) AND n.confirmed_at<:corte
          AND (n.voided_at IS NULL OR n.voided_at>=:corte) GROUP BY a.purchase_invoice_id),
      deuda AS (
        SELECT f.id factura_id,f.supplier_id proveedor_id,f.internal_number factura,s.trade_name proveedor,
          s.tax_identification identificacion,f.supplier_invoice_number numero_proveedor,f.fiscal_number numero_fiscal,
          f.invoice_date fecha,a.due_date vencimiento,f.branch_id sucursal_id,f.currency_id moneda_id,
          m.codigo_iso moneda,m.simbolo,a.original_amount total,
          least(a.original_amount,coalesce(p.aplicado,0)+coalesce(n.aplicado,0)) aplicado,
          greatest(a.original_amount-coalesce(p.aplicado,0)-coalesce(n.aplicado,0),0) saldo
        FROM accounts_payable a JOIN purchase_invoice f ON f.id=a.purchase_invoice_id
        JOIN supplier s ON s.id=f.supplier_id JOIN monedas m ON m.id=f.currency_id
        LEFT JOIN pagos p ON p.purchase_invoice_id=f.id LEFT JOIN notas n ON n.purchase_invoice_id=f.id
        WHERE f.tenant_id=:tenant AND f.empresa_id=:empresa AND a.tenant_id=:tenant AND a.empresa_id=:empresa
          AND f.registered_at<:corte AND a.created_at<:corte AND (f.voided_at IS NULL OR f.voided_at>=:corte)
          AND (:todasSucursales OR f.branch_id IN (:sucursales)))
      """;
    private static final String CREDITS="""
      ,aplicado_notas AS (SELECT a.supplier_credit_note_id id,sum(a.amount) aplicado FROM supplier_credit_application a
        WHERE a.tenant_id=:tenant AND a.empresa_id=:empresa AND a.applied_at<:corte AND (a.reversed=false OR a.reversed_at>=:corte) GROUP BY a.supplier_credit_note_id),
      aplicado_pagos AS (SELECT a.supplier_payment_id id,sum(a.amount) aplicado FROM supplier_payment_application a
        WHERE a.tenant_id=:tenant AND a.empresa_id=:empresa AND a.applied_at<:corte AND (a.reversed=false OR a.reversed_at>=:corte) GROUP BY a.supplier_payment_id),
      creditos AS (SELECT proveedor_id,moneda_id,sum(disponible) disponible FROM (
        SELECT n.supplier_id proveedor_id,n.currency_id moneda_id,greatest(n.total-coalesce(a.aplicado,0),0) disponible
        FROM supplier_credit_note n LEFT JOIN aplicado_notas a ON a.id=n.id WHERE n.tenant_id=:tenant AND n.empresa_id=:empresa
          AND n.confirmed_at<:corte AND (n.voided_at IS NULL OR n.voided_at>=:corte)
          AND (CAST(:proveedor AS uuid) IS NULL OR n.supplier_id=CAST(:proveedor AS uuid))
        UNION ALL SELECT p.supplier_id,p.currency_id,greatest(p.monto-coalesce(a.aplicado,0),0)
        FROM supplier_payment p LEFT JOIN aplicado_pagos a ON a.id=p.id WHERE p.tenant_id=:tenant AND p.empresa_id=:empresa
          AND p.confirmed_at<:corte AND (p.voided_at IS NULL OR p.voided_at>=:corte)
          AND (CAST(:proveedor AS uuid) IS NULL OR p.supplier_id=CAST(:proveedor AS uuid))) x
        WHERE disponible>0 GROUP BY proveedor_id,moneda_id)
      """;
    private static final String WHERE="""
      (:buscar='' OR lower(d.proveedor) LIKE lower('%'||:buscar||'%') OR lower(coalesce(d.identificacion,'')) LIKE lower('%'||:buscar||'%')
        OR lower(d.factura) LIKE lower('%'||:buscar||'%') OR lower(d.numero_proveedor) LIKE lower('%'||:buscar||'%')
        OR lower(coalesce(d.numero_fiscal,'')) LIKE lower('%'||:buscar||'%'))
      AND (CAST(:proveedor AS uuid) IS NULL OR d.proveedor_id=CAST(:proveedor AS uuid))
      AND (CAST(:moneda AS uuid) IS NULL OR d.moneda_id=CAST(:moneda AS uuid))
      AND (CAST(:sucursal AS uuid) IS NULL OR d.sucursal_id=CAST(:sucursal AS uuid))
      AND (CAST(:venceDesde AS date) IS NULL OR d.vencimiento>=CAST(:venceDesde AS date))
      AND (CAST(:venceHasta AS date) IS NULL OR d.vencimiento<=CAST(:venceHasta AS date))
      AND (:estado='TODAS' OR (:estado='PENDIENTES' AND d.saldo>0) OR (:estado='PARCIAL' AND d.aplicado>0 AND d.saldo>0) OR (:estado='PAGADAS' AND d.saldo=0))
      AND (:situacion='TODAS' OR (:situacion='VENCIDAS' AND d.saldo>0 AND d.vencimiento<:al)
        OR (:situacion='HOY' AND d.saldo>0 AND d.vencimiento=:al) OR (:situacion='POR_VENCER' AND d.saldo>0 AND d.vencimiento>:al))
      """;
}
