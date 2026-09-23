package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.EmpresaModulo;
import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;import java.util.*;
public interface EmpresaModuloRepository extends JpaRepository<EmpresaModulo,EmpresaModulo.Clave>{
    List<EmpresaModulo> findAllByTenantIdAndEmpresaId(UUID tenantId,UUID empresaId);
    List<EmpresaModulo> findAllByTenantIdAndEmpresaIdInAndEnabledTrue(UUID tenantId,Collection<UUID> empresaIds);
    List<EmpresaModulo> findAllByEmpresaIdInAndEnabledTrue(Collection<UUID> empresaIds);
    Optional<EmpresaModulo> findByTenantIdAndEmpresaIdAndModuleKey(UUID tenantId,UUID empresaId,String moduleKey);
    @Query("select em.moduleKey from EmpresaModulo em where em.tenantId=:tenant and em.empresaId=:empresa and em.enabled=true")
    Set<String> findEnabledKeys(@Param("tenant")UUID tenantId,@Param("empresa")UUID empresaId);
    void deleteAllByTenantIdAndEmpresaId(UUID tenantId,UUID empresaId);
}
