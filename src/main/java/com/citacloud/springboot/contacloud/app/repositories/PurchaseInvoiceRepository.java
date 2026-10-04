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
    boolean existsByTenantIdAndEmpresaIdAndProveedorIdAndNumeroNormalizado(UUID tenantId,UUID empresaId,UUID proveedorId,String numero);
    boolean existsByTenantIdAndEmpresaIdAndProveedorIdAndNumeroNormalizadoAndIdNot(UUID tenantId,UUID empresaId,UUID proveedorId,String numero,UUID id);
}
