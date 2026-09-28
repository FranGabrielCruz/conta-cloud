package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.ConfiguracionContable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ConfiguracionContableRepository extends JpaRepository<ConfiguracionContable, UUID> {
    Optional<ConfiguracionContable> findByTenantIdAndEmpresaId(UUID tenantId, UUID empresaId);
}
