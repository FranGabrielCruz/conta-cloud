package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.Almacen;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface AlmacenRepository extends JpaRepository<Almacen,UUID> {
    List<Almacen> findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(UUID tenantId,UUID empresaId);
    Optional<Almacen> findByIdAndTenantIdAndEmpresaId(UUID id,UUID tenantId,UUID empresaId);
}
