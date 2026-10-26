package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ConceptoMovimientoCajaRepository extends JpaRepository<ConceptoMovimientoCaja,UUID> {
    List<ConceptoMovimientoCaja> findAllByTenantIdAndEmpresaIdAndActiveTrueOrderByTipoAscNombreAsc(UUID tenant,UUID empresa);
    Optional<ConceptoMovimientoCaja> findByIdAndTenantIdAndEmpresaIdAndActiveTrue(UUID id,UUID tenant,UUID empresa);
}
