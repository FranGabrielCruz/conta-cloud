package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.DistribucionBorradorPagoProveedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SupplierPaymentDraftAllocationRepository extends JpaRepository<DistribucionBorradorPagoProveedor,UUID> {
    @Query("""
        select distribution from DistribucionBorradorPagoProveedor distribution
         where distribution.pagoId=:pago and distribution.tenantId=:tenant and distribution.empresaId=:empresa
         order by distribution.facturaId
        """)
    List<DistribucionBorradorPagoProveedor> findAllByPagoIdAndTenantIdAndEmpresaId(
        @Param("pago") UUID pago,@Param("tenant") UUID tenant,@Param("empresa") UUID empresa);

    void deleteAllByPagoIdAndTenantIdAndEmpresaId(UUID pago,UUID tenant,UUID empresa);
}
