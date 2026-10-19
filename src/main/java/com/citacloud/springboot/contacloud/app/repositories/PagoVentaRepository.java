package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.PagoVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PagoVentaRepository extends JpaRepository<PagoVenta,UUID>{
    Optional<PagoVenta> findByFacturaIdAndTenantIdAndEmpresaId(UUID facturaId,UUID tenantId,UUID empresaId);
}
