package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.TerminalPos;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface TerminalPosRepository extends JpaRepository<TerminalPos,UUID>{
    Optional<TerminalPos> findByTokenHashAndTenantIdAndEmpresaIdAndActivoTrue(String hash,UUID tenant,UUID empresa);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TerminalPos t where t.id=:id and t.tenantId=:tenant and t.empresaId=:empresa and t.activo=true")
    Optional<TerminalPos> bloquear(@Param("id")UUID id,@Param("tenant")UUID tenant,@Param("empresa")UUID empresa);
}
