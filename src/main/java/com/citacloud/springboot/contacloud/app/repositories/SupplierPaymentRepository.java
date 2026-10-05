package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.PagoProveedor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface SupplierPaymentRepository extends JpaRepository<PagoProveedor,UUID> {
    Optional<PagoProveedor> findByFacturaIdAndTenantIdAndEmpresaId(UUID facturaId,UUID tenantId,UUID empresaId);
    Optional<PagoProveedor> findByClaveIdempotenciaAndTenantIdAndEmpresaId(UUID clave,UUID tenantId,UUID empresaId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PagoProveedor p where p.facturaId=:factura and p.tenantId=:tenant and p.empresaId=:empresa")
    Optional<PagoProveedor> bloquearPorFactura(@Param("factura")UUID factura,@Param("tenant")UUID tenant,
        @Param("empresa")UUID empresa);
}
