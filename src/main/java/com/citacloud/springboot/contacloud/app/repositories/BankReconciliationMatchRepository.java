package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.AsociacionConciliacionBancaria;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface BankReconciliationMatchRepository extends JpaRepository<AsociacionConciliacionBancaria,UUID>{
 Page<AsociacionConciliacionBancaria> findAllByTenantIdAndEmpresaIdAndConciliacionId(UUID tenant,UUID empresa,UUID conciliacion,Pageable p);
 List<AsociacionConciliacionBancaria> findAllByTenantIdAndEmpresaIdAndConciliacionId(UUID tenant,UUID empresa,UUID conciliacion);
 long countByTenantIdAndEmpresaIdAndConciliacionId(UUID tenant,UUID empresa,UUID conciliacion);
 boolean existsByMovimientoFinancieroId(UUID id); boolean existsByMovimientoBancarioId(UUID id);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 Optional<AsociacionConciliacionBancaria> findByIdAndTenantIdAndEmpresaIdAndConciliacionId(UUID id,UUID tenant,UUID empresa,UUID conciliacion);
 void deleteAllByTenantIdAndEmpresaIdAndConciliacionId(UUID tenant,UUID empresa,UUID conciliacion);
}
