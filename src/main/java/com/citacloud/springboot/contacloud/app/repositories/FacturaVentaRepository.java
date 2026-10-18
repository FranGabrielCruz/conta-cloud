package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.FacturaVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface FacturaVentaRepository extends JpaRepository<FacturaVenta,UUID>{
    Optional<FacturaVenta> findByTenantIdAndEmpresaIdAndClaveIdempotencia(UUID tenant,UUID empresa,UUID clave);
    Optional<FacturaVenta> findByIdAndTenantIdAndEmpresaId(UUID id,UUID tenant,UUID empresa);
}
