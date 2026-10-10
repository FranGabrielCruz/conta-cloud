package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.AplicacionPagoProveedor;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface SupplierPaymentApplicationRepository extends JpaRepository<AplicacionPagoProveedor,UUID>{
 List<AplicacionPagoProveedor> findAllByPagoIdAndTenantIdAndEmpresaIdAndReversedFalseOrderByAplicadaEn(UUID pago,UUID tenant,UUID empresa);
}
