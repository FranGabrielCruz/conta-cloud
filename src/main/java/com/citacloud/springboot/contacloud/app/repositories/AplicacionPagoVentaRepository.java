package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.AplicacionPagoVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AplicacionPagoVentaRepository extends JpaRepository<AplicacionPagoVenta,UUID>{
    List<AplicacionPagoVenta> findAllByPagoIdAndTenantIdAndEmpresaIdOrderByLineOrder(UUID pagoId,UUID tenantId,UUID empresaId);
}
