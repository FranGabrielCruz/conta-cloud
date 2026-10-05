package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.AplicacionNotaCreditoProveedor;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;import java.math.BigDecimal;import java.util.*;
public interface SupplierCreditApplicationRepository extends JpaRepository<AplicacionNotaCreditoProveedor,UUID>{
 List<AplicacionNotaCreditoProveedor> findAllByNotaIdAndTenantIdAndEmpresaIdAndReversedFalseOrderByAplicadaEn(UUID nota,UUID tenant,UUID empresa);
 boolean existsByNotaIdAndTenantIdAndEmpresaIdAndReversedFalse(UUID nota,UUID tenant,UUID empresa);
 @Query("select coalesce(sum(a.amount),0) from AplicacionNotaCreditoProveedor a where a.tenantId=:tenant and a.empresaId=:empresa and a.facturaId=:factura and a.reversed=false") BigDecimal totalAplicadoFactura(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("factura")UUID factura);
}
