package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.ProductoCategoria;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ProductoCategoriaRepository extends JpaRepository<ProductoCategoria,UUID> {
    List<ProductoCategoria> findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(UUID tenant,UUID empresa);
    Optional<ProductoCategoria> findByIdAndTenantIdAndEmpresaId(UUID id,UUID tenant,UUID empresa);
}
