package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.LineaDevolucionVenta;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.*;
public interface LineaDevolucionVentaRepository extends JpaRepository<LineaDevolucionVenta,UUID>{
    @Query("select coalesce(sum(l.cantidad),0) from LineaDevolucionVenta l where l.tenantId=:tenant and l.empresaId=:empresa and l.lineaFacturaId=:linea")
    BigDecimal cantidadDevuelta(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("linea")UUID lineaFactura);
    List<LineaDevolucionVenta> findAllByDevolucionIdOrderByOrden(UUID devolucionId);
    List<LineaDevolucionVenta> findAllByDevolucionIdAndTenantIdAndEmpresaIdOrderByOrden(UUID devolucionId,UUID tenantId,UUID empresaId);
}
