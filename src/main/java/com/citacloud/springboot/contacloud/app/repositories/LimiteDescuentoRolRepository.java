package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.LimiteDescuentoRol;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface LimiteDescuentoRolRepository extends JpaRepository<LimiteDescuentoRol,UUID>{List<LimiteDescuentoRol> findAllByTenantIdAndEmpresaIdOrderByRolNombre(UUID tenant,UUID empresa);Optional<LimiteDescuentoRol> findByIdAndTenantIdAndEmpresaId(UUID id,UUID tenant,UUID empresa);Optional<LimiteDescuentoRol> findByTenantIdAndEmpresaIdAndRolIdAndActiveTrue(UUID tenant,UUID empresa,UUID rol);boolean existsByTenantIdAndEmpresaIdAndRolId(UUID tenant,UUID empresa,UUID rol);}
