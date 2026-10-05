package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.CuentaPagar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface AccountsPayableRepository extends JpaRepository<CuentaPagar,UUID> {
    Optional<CuentaPagar> findByFacturaIdAndTenantIdAndEmpresaId(UUID facturaId,UUID tenantId,UUID empresaId);
    boolean existsByFacturaId(UUID facturaId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CuentaPagar c where c.facturaId=:factura and c.tenantId=:tenant and c.empresaId=:empresa")
    Optional<CuentaPagar> bloquearPorFactura(@Param("factura")UUID factura,@Param("tenant")UUID tenant,@Param("empresa")UUID empresa);
    @Query("select c from CuentaPagar c, FacturaProveedor f where f.id=c.facturaId and c.tenantId=:tenant and c.empresaId=:empresa and c.proveedorId=:proveedor and c.monedaId=:moneda and c.voided=false and c.montoAplicado<c.montoOriginal order by c.vencimiento,f.fecha,f.numeroInterno")
    List<CuentaPagar> pendientes(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("proveedor")UUID proveedor,@Param("moneda")UUID moneda);
}
