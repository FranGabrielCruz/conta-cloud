package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.ConceptoValeCaja;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface ConceptoValeCajaRepository extends JpaRepository<ConceptoValeCaja,UUID>{
 List<ConceptoValeCaja> findAllByTenantIdAndEmpresaIdAndActiveTrueOrderByNameAsc(UUID tenant,UUID empresa);
 Optional<ConceptoValeCaja> findByIdAndTenantIdAndEmpresaIdAndActiveTrue(UUID id,UUID tenant,UUID empresa);
}
