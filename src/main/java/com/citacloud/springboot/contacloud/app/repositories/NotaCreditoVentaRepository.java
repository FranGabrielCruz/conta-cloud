package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.NotaCreditoVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface NotaCreditoVentaRepository extends JpaRepository<NotaCreditoVenta,UUID>{
    Optional<NotaCreditoVenta> findByDevolucionId(UUID devolucionId);
    Optional<NotaCreditoVenta> findByDevolucionIdAndTenantIdAndEmpresaId(UUID devolucionId,UUID tenantId,UUID empresaId);
}
