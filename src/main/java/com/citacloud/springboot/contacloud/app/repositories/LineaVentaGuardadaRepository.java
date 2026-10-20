package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.LineaVentaGuardada;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface LineaVentaGuardadaRepository extends JpaRepository<LineaVentaGuardada,UUID>{
 List<LineaVentaGuardada> findAllByVentaIdAndTenantIdAndEmpresaIdOrderByOrden(UUID ventaId,UUID tenant,UUID empresa);
 void deleteAllByVentaIdAndTenantIdAndEmpresaId(UUID ventaId,UUID tenant,UUID empresa);
}
