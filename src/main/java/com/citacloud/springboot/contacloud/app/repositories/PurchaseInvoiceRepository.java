package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;

public interface PurchaseInvoiceRepository extends JpaRepository<FacturaProveedor,UUID> {
    @EntityGraph(attributePaths={"proveedor","sucursal","moneda","condicionPago","ordenCompra"})
    @Query("""
      select f from FacturaProveedor f where f.tenantId=:tenant and f.empresaId=:empresa
      and (:buscar='' or lower(f.numeroProveedor) like lower(concat('%',:buscar,'%'))
        or lower(coalesce(f.numeroFiscal,'')) like lower(concat('%',:buscar,'%'))
        or lower(f.proveedor.nombreComercial) like lower(concat('%',:buscar,'%')))
      and (:desde is null or f.fecha>=:desde) and (:hasta is null or f.fecha<=:hasta)
      and (:estado is null or f.status=:estado)
      """)
    Page<FacturaProveedor> buscar(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,
        @Param("buscar")String buscar,@Param("desde")LocalDate desde,@Param("hasta")LocalDate hasta,
        @Param("estado")EstadoFacturaProveedor estado,Pageable pageable);
    @EntityGraph(attributePaths={"lineas","proveedor","sucursal","moneda","condicionPago","ordenCompra"})
    Optional<FacturaProveedor> findByIdAndTenantIdAndEmpresaId(UUID id,UUID tenantId,UUID empresaId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths={"lineas","proveedor","sucursal","moneda","condicionPago","ordenCompra"})
    @Query("select f from FacturaProveedor f where f.id=:id and f.tenantId=:tenant and f.empresaId=:empresa")
    Optional<FacturaProveedor> bloquear(@Param("id")UUID id,@Param("tenant")UUID tenant,@Param("empresa")UUID empresa);
    @EntityGraph(attributePaths={"proveedor","moneda","ordenCompra"})
    @Query("""
      select f from FacturaProveedor f
      where f.tenantId=:tenant and f.empresaId=:empresa
      and f.status=com.citacloud.springboot.contacloud.app.models.EstadoFacturaProveedor.REGISTERED
      and (:proveedor is null or f.proveedorId=:proveedor)
      and (:buscar='' or lower(f.numeroProveedor) like lower(concat('%',:buscar,'%'))
        or lower(coalesce(f.numeroFiscal,'')) like lower(concat('%',:buscar,'%')))
      """)
    Page<FacturaProveedor> registradasParaRecepcion(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,
        @Param("proveedor")UUID proveedor,@Param("buscar")String buscar,Pageable pageable);
    boolean existsByTenantIdAndEmpresaIdAndProveedorIdAndNumeroNormalizado(UUID tenantId,UUID empresaId,UUID proveedorId,String numero);
    boolean existsByTenantIdAndEmpresaIdAndProveedorIdAndNumeroNormalizadoAndIdNot(UUID tenantId,UUID empresaId,UUID proveedorId,String numero,UUID id);
    @EntityGraph(attributePaths={"proveedor","moneda"})
    @Query("""
      select f from FacturaProveedor f
      where f.tenantId=:tenant and f.empresaId=:empresa and f.proveedorId=:proveedor and f.monedaId=:moneda
      and f.status=com.citacloud.springboot.contacloud.app.models.EstadoFacturaProveedor.REGISTERED
      and (:buscar='' or lower(f.numeroInterno) like lower(concat('%',:buscar,'%'))
        or lower(f.numeroProveedor) like lower(concat('%',:buscar,'%'))
        or lower(coalesce(f.numeroFiscal,'')) like lower(concat('%',:buscar,'%')))
      """)
    Page<FacturaProveedor> buscarParaNotaCredito(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,
        @Param("proveedor")UUID proveedor,@Param("moneda")UUID moneda,@Param("buscar")String buscar,Pageable pageable);
    @EntityGraph(attributePaths={"proveedor","moneda"})
    @Query("""
      select f from FacturaProveedor f, CuentaPagar c
      where c.facturaId=f.id and f.tenantId=:tenant and f.empresaId=:empresa
      and c.tenantId=:tenant and c.empresaId=:empresa and c.voided=false and c.montoAplicado<c.montoOriginal
      and f.proveedorId=:proveedor and f.monedaId=:moneda
      and f.status=com.citacloud.springboot.contacloud.app.models.EstadoFacturaProveedor.REGISTERED
      and (:buscar='' or lower(f.numeroInterno) like lower(concat('%',:buscar,'%'))
        or lower(f.numeroProveedor) like lower(concat('%',:buscar,'%'))
        or lower(coalesce(f.numeroFiscal,'')) like lower(concat('%',:buscar,'%')))
      """)
    Page<FacturaProveedor> buscarPendientesParaAplicar(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,
        @Param("proveedor")UUID proveedor,@Param("moneda")UUID moneda,@Param("buscar")String buscar,Pageable pageable);
}
