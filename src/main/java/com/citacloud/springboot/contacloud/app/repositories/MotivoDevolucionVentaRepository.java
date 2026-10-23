package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.MotivoDevolucionVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface MotivoDevolucionVentaRepository extends JpaRepository<MotivoDevolucionVenta,UUID>{
    List<MotivoDevolucionVenta> findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(UUID tenant,UUID empresa);
    Optional<MotivoDevolucionVenta> findByIdAndTenantIdAndEmpresaIdAndActivoTrue(UUID id,UUID tenant,UUID empresa);
}
