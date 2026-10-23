package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.DevolucionVenta;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface DevolucionVentaRepository extends JpaRepository<DevolucionVenta,UUID>{
    Optional<DevolucionVenta> findByIdAndTenantIdAndEmpresaId(UUID id,UUID tenant,UUID empresa);
    @Query("select count(d) from DevolucionVenta d where d.tenantId=:tenant and d.empresaId=:empresa and d.facturaId=:factura")
    long contarPorFactura(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("factura")UUID factura);
}
