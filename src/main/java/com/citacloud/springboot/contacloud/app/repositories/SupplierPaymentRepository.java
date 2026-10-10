package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.*;import jakarta.persistence.LockModeType;import org.springframework.data.domain.*;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;import java.time.LocalDate;import java.util.*;
public interface SupplierPaymentRepository extends JpaRepository<PagoProveedor,UUID>{
 Optional<PagoProveedor> findByFacturaIdAndTenantIdAndEmpresaId(UUID facturaId,UUID tenantId,UUID empresaId);Optional<PagoProveedor> findByClaveIdempotenciaAndTenantIdAndEmpresaId(UUID clave,UUID tenantId,UUID empresaId);
 @EntityGraph(attributePaths={"proveedor","moneda","caja","cuentaBancaria"})
 @Query("""
 select distinct p from PagoProveedor p where p.tenantId=:tenant and p.empresaId=:empresa
 and (:proveedor is null or p.proveedorId=:proveedor) and (:estado is null or p.status=:estado)
 and (:medio is null or p.medioPago=:medio) and (:moneda is null or p.monedaId=:moneda)
 and (:desde is null or p.fecha>=:desde) and (:hasta is null or p.fecha<=:hasta)
 and (:buscar='' or lower(p.numero) like lower(concat('%',:buscar,'%')) or lower(p.proveedor.nombreComercial) like lower(concat('%',:buscar,'%'))
  or lower(coalesce(p.proveedor.identificacionFiscal,'')) like lower(concat('%',:buscar,'%')) or lower(coalesce(p.referencia,'')) like lower(concat('%',:buscar,'%'))
  or lower(coalesce(p.numeroCheque,'')) like lower(concat('%',:buscar,'%')) or exists(select a.id from AplicacionPagoProveedor a, FacturaProveedor f where a.pagoId=p.id and a.facturaId=f.id and a.reversed=false and (lower(f.numeroInterno) like lower(concat('%',:buscar,'%')) or lower(f.numeroProveedor) like lower(concat('%',:buscar,'%')))))
 """) Page<PagoProveedor> buscar(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("buscar")String buscar,@Param("proveedor")UUID proveedor,@Param("estado")EstadoPagoProveedor estado,@Param("medio")MedioPagoMovimiento medio,@Param("moneda")UUID moneda,@Param("desde")LocalDate desde,@Param("hasta")LocalDate hasta,Pageable pageable);
 @EntityGraph(attributePaths={"proveedor","moneda","caja","cuentaBancaria"}) Optional<PagoProveedor> findByIdAndTenantIdAndEmpresaId(UUID id,UUID tenant,UUID empresa);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from PagoProveedor p where p.id=:id and p.tenantId=:tenant and p.empresaId=:empresa") Optional<PagoProveedor> bloquear(@Param("id")UUID id,@Param("tenant")UUID tenant,@Param("empresa")UUID empresa);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from PagoProveedor p where p.facturaId=:factura and p.tenantId=:tenant and p.empresaId=:empresa") Optional<PagoProveedor> bloquearPorFactura(@Param("factura")UUID factura,@Param("tenant")UUID tenant,@Param("empresa")UUID empresa);
 @Query("""
 select (count(p)>0) from PagoProveedor p
 where p.tenantId=:tenant and p.empresaId=:empresa and p.cuentaBancariaId=:cuenta
 and upper(p.numeroCheque)=upper(:cheque) and p.status<>:anulado
 and (:excluir is null or p.id<>:excluir)
 """)
 boolean existeChequeActivo(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,
     @Param("cuenta")UUID cuenta,@Param("cheque")String cheque,
     @Param("anulado")EstadoPagoProveedor anulado,@Param("excluir")UUID excluir);
 @Query("select coalesce(sum(p.monto-p.montoAplicado),0) from PagoProveedor p where p.tenantId=:tenant and p.empresaId=:empresa and p.proveedorId=:proveedor and p.monedaId=:moneda and p.status in (com.citacloud.springboot.contacloud.app.models.EstadoPagoProveedor.AVAILABLE,com.citacloud.springboot.contacloud.app.models.EstadoPagoProveedor.PARTIALLY_APPLIED)") java.math.BigDecimal disponibleProveedor(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("proveedor")UUID proveedor,@Param("moneda")UUID moneda);
}
