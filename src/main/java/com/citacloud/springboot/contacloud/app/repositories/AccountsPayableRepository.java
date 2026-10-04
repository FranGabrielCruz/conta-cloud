package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.CuentaPagar;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface AccountsPayableRepository extends JpaRepository<CuentaPagar,UUID> {
    Optional<CuentaPagar> findByFacturaIdAndTenantIdAndEmpresaId(UUID facturaId,UUID tenantId,UUID empresaId);
    boolean existsByFacturaId(UUID facturaId);
}
