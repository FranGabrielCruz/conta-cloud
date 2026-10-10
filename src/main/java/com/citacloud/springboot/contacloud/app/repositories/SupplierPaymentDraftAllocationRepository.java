package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.DistribucionBorradorPagoProveedor;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface SupplierPaymentDraftAllocationRepository extends JpaRepository<DistribucionBorradorPagoProveedor,UUID>{List<DistribucionBorradorPagoProveedor> findAllByPagoIdAndTenantIdAndEmpresaId(UUID pago,UUID tenant,UUID empresa);void deleteAllByPagoIdAndTenantIdAndEmpresaId(UUID pago,UUID tenant,UUID empresa);}
