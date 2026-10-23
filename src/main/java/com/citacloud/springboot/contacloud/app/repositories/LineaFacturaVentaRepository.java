package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.LineaFacturaVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface LineaFacturaVentaRepository extends JpaRepository<LineaFacturaVenta,UUID>{
    List<LineaFacturaVenta> findAllByFacturaIdOrderByOrden(UUID facturaId);
    List<LineaFacturaVenta> findAllByFacturaIdAndTenantIdAndEmpresaIdOrderByOrden(UUID facturaId,UUID tenantId,UUID empresaId);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LineaFacturaVenta l where l.facturaId=:factura and l.tenantId=:tenant and l.empresaId=:empresa order by l.id")
    List<LineaFacturaVenta> bloquearPorFactura(@Param("factura")UUID factura,@Param("tenant")UUID tenant,@Param("empresa")UUID empresa);
}
