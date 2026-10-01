package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface BankStatementMovementRepository extends JpaRepository<MovimientoEstadoBancario,UUID>{
 @Query("""
   select b from MovimientoEstadoBancario b where b.tenantId=:tenant and b.empresaId=:empresa
   and b.conciliacionId=:conciliacion and not exists(select m.id from AsociacionConciliacionBancaria m
   where m.movimientoBancarioId=b.id)
   """)
 Page<MovimientoEstadoBancario> pendientes(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,
   @Param("conciliacion")UUID conciliacion,Pageable pageable);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 Optional<MovimientoEstadoBancario> findByIdAndTenantIdAndEmpresaIdAndConciliacionId(UUID id,UUID tenant,UUID empresa,UUID conciliacion);
 List<MovimientoEstadoBancario> findAllByTenantIdAndEmpresaIdAndConciliacionId(UUID tenant,UUID empresa,UUID conciliacion);
 long countByTenantIdAndEmpresaIdAndConciliacionId(UUID tenant,UUID empresa,UUID conciliacion);
}
