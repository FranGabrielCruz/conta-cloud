package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.DireccionCliente;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface DireccionClienteRepository extends JpaRepository<DireccionCliente,UUID> {
    List<DireccionCliente> findAllByTenantIdAndEmpresaIdAndClienteIdAndActiveTrueOrderByPrincipalDescAddressAsc(UUID tenant,UUID empresa,UUID cliente);
    List<DireccionCliente> findAllByTenantIdAndEmpresaIdAndClienteIdInAndActiveTrueAndPrincipalTrue(UUID tenant,UUID empresa,Collection<UUID> clientes);
    Optional<DireccionCliente> findFirstByTenantIdAndEmpresaIdAndClienteIdAndActiveTrueAndPrincipalTrue(UUID tenant,UUID empresa,UUID cliente);
    Optional<DireccionCliente> findByIdAndTenantIdAndEmpresaIdAndClienteId(UUID id,UUID tenant,UUID empresa,UUID cliente);
}
