package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.ContactoCliente;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ContactoClienteRepository extends JpaRepository<ContactoCliente,UUID> {
    List<ContactoCliente> findAllByTenantIdAndEmpresaIdAndClienteIdAndActiveTrueOrderByPrincipalDescNombreAsc(UUID tenant,UUID empresa,UUID cliente);
    Optional<ContactoCliente> findByIdAndTenantIdAndEmpresaIdAndClienteId(UUID id,UUID tenant,UUID empresa,UUID cliente);
}
