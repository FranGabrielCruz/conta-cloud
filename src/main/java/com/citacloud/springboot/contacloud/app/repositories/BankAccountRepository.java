package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.CuentaBancaria;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface BankAccountRepository extends JpaRepository<CuentaBancaria, UUID> {
    @EntityGraph(attributePaths = "moneda")
    @Query("""
        select c from CuentaBancaria c
        where c.tenantId=:tenantId and c.empresaId=:empresaId
          and (:buscar='' or lower(c.bancoNombre) like lower(concat('%',:buscar,'%'))
            or lower(c.nombreCuenta) like lower(concat('%',:buscar,'%')))
          and (:monedaId is null or c.monedaId=:monedaId)
          and (:activo is null or c.activo=:activo)
        """)
    Page<CuentaBancaria> buscar(@Param("tenantId") UUID tenantId, @Param("empresaId") UUID empresaId,
        @Param("buscar") String buscar, @Param("monedaId") UUID monedaId,
        @Param("activo") Boolean activo, Pageable pageable);

    @EntityGraph(attributePaths = "moneda")
    Optional<CuentaBancaria> findByIdAndTenantIdAndEmpresaId(UUID id, UUID tenantId, UUID empresaId);

    boolean existsByTenantIdAndEmpresaIdAndNumeroCuentaFingerprint(
        UUID tenantId, UUID empresaId, String fingerprint);

    boolean existsByTenantIdAndEmpresaIdAndNumeroCuentaFingerprintAndIdNot(
        UUID tenantId, UUID empresaId, String fingerprint, UUID id);
}
