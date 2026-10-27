package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.CierreCaja; import org.springframework.data.domain.*; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface CierreCajaRepository extends JpaRepository<CierreCaja,UUID>{
 Optional<CierreCaja> findByIdAndTenantIdAndEmpresaId(UUID id,UUID tenant,UUID empresa);
 Optional<CierreCaja> findByTenantIdAndEmpresaIdAndSesionId(UUID tenant,UUID empresa,UUID sesion);
 Page<CierreCaja> findAllByTenantIdAndEmpresaId(UUID tenant,UUID empresa,Pageable pageable);
 boolean existsByTenantIdAndEmpresaIdAndEmpresaLogo(UUID tenant,UUID empresa,String key);
}
