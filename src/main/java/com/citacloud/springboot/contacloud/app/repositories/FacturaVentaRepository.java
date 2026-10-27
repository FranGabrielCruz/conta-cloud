package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;
public interface FacturaVentaRepository extends JpaRepository<FacturaVenta,UUID>{
    Optional<FacturaVenta> findByTenantIdAndEmpresaIdAndClaveIdempotencia(UUID tenant,UUID empresa,UUID clave);
    Optional<FacturaVenta> findByIdAndTenantIdAndEmpresaId(UUID id,UUID tenant,UUID empresa);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FacturaVenta f where f.id=:id and f.tenantId=:tenant and f.empresaId=:empresa")
    Optional<FacturaVenta> bloquear(@Param("id")UUID id,@Param("tenant")UUID tenant,@Param("empresa")UUID empresa);

    @Query(value="""
        SELECT f.* FROM sales_invoice f
        WHERE f.tenant_id=:tenant AND f.empresa_id=:empresa AND f.status='CONFIRMED'
          AND (:todasSucursales OR f.branch_id IN (:sucursales))
          AND (:desde IS NULL OR f.invoice_date>=:desde)
          AND (:hasta IS NULL OR f.invoice_date<=:hasta)
          AND (:texto='' OR lower(f.internal_number) LIKE concat('%',:texto,'%')
               OR lower(f.customer_name) LIKE concat('%',:texto,'%')
               OR (:identificacion<>'' AND f.customer_identification_normalized=:identificacion))
          AND (
            :estado='TODAS'
            OR (:estado='PAGADA' AND NOT EXISTS(SELECT 1 FROM sales_return r WHERE r.tenant_id=f.tenant_id AND r.empresa_id=f.empresa_id AND r.sales_invoice_id=f.id))
            OR (:estado='PARCIALMENTE_DEVUELTA'
                AND EXISTS(SELECT 1 FROM sales_return r WHERE r.tenant_id=f.tenant_id AND r.empresa_id=f.empresa_id AND r.sales_invoice_id=f.id)
                AND EXISTS(SELECT 1 FROM sales_invoice_line il WHERE il.sales_invoice_id=f.id AND il.quantity>(SELECT COALESCE(SUM(rl.returned_quantity),0) FROM sales_return_line rl WHERE rl.tenant_id=f.tenant_id AND rl.empresa_id=f.empresa_id AND rl.sales_invoice_line_id=il.id)))
            OR (:estado='DEVUELTA'
                AND EXISTS(SELECT 1 FROM sales_return r WHERE r.tenant_id=f.tenant_id AND r.empresa_id=f.empresa_id AND r.sales_invoice_id=f.id)
                AND NOT EXISTS(SELECT 1 FROM sales_invoice_line il WHERE il.sales_invoice_id=f.id AND il.quantity>(SELECT COALESCE(SUM(rl.returned_quantity),0) FROM sales_return_line rl WHERE rl.tenant_id=f.tenant_id AND rl.empresa_id=f.empresa_id AND rl.sales_invoice_line_id=il.id)))
          )
        ORDER BY f.confirmed_at DESC,f.id DESC
        """,countQuery="""
        SELECT count(*) FROM sales_invoice f
        WHERE f.tenant_id=:tenant AND f.empresa_id=:empresa AND f.status='CONFIRMED'
          AND (:todasSucursales OR f.branch_id IN (:sucursales))
          AND (:desde IS NULL OR f.invoice_date>=:desde) AND (:hasta IS NULL OR f.invoice_date<=:hasta)
          AND (:texto='' OR lower(f.internal_number) LIKE concat('%',:texto,'%') OR lower(f.customer_name) LIKE concat('%',:texto,'%') OR (:identificacion<>'' AND f.customer_identification_normalized=:identificacion))
          AND (:estado='TODAS'
            OR (:estado='PAGADA' AND NOT EXISTS(SELECT 1 FROM sales_return r WHERE r.tenant_id=f.tenant_id AND r.empresa_id=f.empresa_id AND r.sales_invoice_id=f.id))
            OR (:estado='PARCIALMENTE_DEVUELTA' AND EXISTS(SELECT 1 FROM sales_return r WHERE r.tenant_id=f.tenant_id AND r.empresa_id=f.empresa_id AND r.sales_invoice_id=f.id) AND EXISTS(SELECT 1 FROM sales_invoice_line il WHERE il.sales_invoice_id=f.id AND il.quantity>(SELECT COALESCE(SUM(rl.returned_quantity),0) FROM sales_return_line rl WHERE rl.tenant_id=f.tenant_id AND rl.empresa_id=f.empresa_id AND rl.sales_invoice_line_id=il.id)))
            OR (:estado='DEVUELTA' AND EXISTS(SELECT 1 FROM sales_return r WHERE r.tenant_id=f.tenant_id AND r.empresa_id=f.empresa_id AND r.sales_invoice_id=f.id) AND NOT EXISTS(SELECT 1 FROM sales_invoice_line il WHERE il.sales_invoice_id=f.id AND il.quantity>(SELECT COALESCE(SUM(rl.returned_quantity),0) FROM sales_return_line rl WHERE rl.tenant_id=f.tenant_id AND rl.empresa_id=f.empresa_id AND rl.sales_invoice_line_id=il.id))))
        """,nativeQuery=true)
    Page<FacturaVenta> buscarParaDevolucion(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,
        @Param("todasSucursales")boolean todasSucursales,@Param("sucursales")Collection<UUID>sucursales,
        @Param("texto")String texto,@Param("identificacion")String identificacion,@Param("desde")LocalDate desde,
        @Param("hasta")LocalDate hasta,@Param("estado")String estado,Pageable pageable);

    @Query(value="""
        SELECT f FROM FacturaVenta f
        WHERE f.tenantId=:tenant AND f.empresaId=:empresa
          AND (:todasSucursales=true OR f.sucursalId IN (:sucursales))
          AND (:desde IS NULL OR f.fecha>=:desde)
          AND (:hasta IS NULL OR f.fecha<=:hasta)
          AND (:estado IS NULL OR f.status=:estado)
          AND (:texto='' OR lower(f.numero) LIKE concat('%',:texto,'%')
               OR lower(f.clienteNombre) LIKE concat('%',:texto,'%')
               OR (:identificacion<>'' AND f.clienteIdentificacionNormalizada=:identificacion))
        ORDER BY CASE WHEN lower(f.numero)=:texto THEN 0 ELSE 1 END,
                 f.confirmadaEn DESC,f.id DESC
        """,countQuery="""
        SELECT count(f) FROM FacturaVenta f
        WHERE f.tenantId=:tenant AND f.empresaId=:empresa
          AND (:todasSucursales=true OR f.sucursalId IN (:sucursales))
          AND (:desde IS NULL OR f.fecha>=:desde)
          AND (:hasta IS NULL OR f.fecha<=:hasta)
          AND (:estado IS NULL OR f.status=:estado)
          AND (:texto='' OR lower(f.numero) LIKE concat('%',:texto,'%')
               OR lower(f.clienteNombre) LIKE concat('%',:texto,'%')
               OR (:identificacion<>'' AND f.clienteIdentificacionNormalizada=:identificacion))
        """)
    Page<FacturaVenta> buscarHistorico(@Param("tenant") UUID tenant,@Param("empresa") UUID empresa,
        @Param("todasSucursales") boolean todasSucursales,@Param("sucursales") Collection<UUID> sucursales,
        @Param("texto") String texto,@Param("identificacion") String identificacion,
        @Param("desde") LocalDate desde,@Param("hasta") LocalDate hasta,@Param("estado") EstadoFacturaVenta estado,Pageable pageable);

    @Query("""
        select f from FacturaVenta f
        where f.tenantId=:tenant and f.empresaId=:empresa and f.sesionCajaId=:sesion
          and (:estado is null or f.status=:estado)
          and (:texto='' or lower(f.numero) like concat('%',:texto,'%')
            or lower(f.clienteNombre) like concat('%',:texto,'%')
            or (:identificacion<>'' and f.clienteIdentificacionNormalizada=:identificacion))
        """)
    Page<FacturaVenta> buscarPorSesion(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,
        @Param("sesion")UUID sesion,@Param("texto")String texto,@Param("identificacion")String identificacion,
        @Param("estado")EstadoFacturaVenta estado,Pageable pageable);

    @Query("""
        select coalesce(sum(f.total),0) from FacturaVenta f
        where f.tenantId=:tenant and f.empresaId=:empresa and f.sesionCajaId=:sesion
          and f.status=com.citacloud.springboot.contacloud.app.models.EstadoFacturaVenta.CONFIRMED
        """)
    java.math.BigDecimal totalConfirmadoPorSesion(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,
        @Param("sesion")UUID sesion);
}
