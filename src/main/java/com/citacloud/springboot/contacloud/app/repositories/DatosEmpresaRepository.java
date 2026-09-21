package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.DatosEmpresa;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface DatosEmpresaRepository extends JpaRepository<DatosEmpresa, UUID> {
    Optional<DatosEmpresa> findByEmpresaId(UUID empresaId);
}
