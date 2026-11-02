package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.DevolucionVenta;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface DevolucionVentaRepository extends JpaRepository<DevolucionVenta,UUID>{
    Optional<DevolucionVenta> findByIdAndTenantIdAndEmpresaId(UUID id,UUID tenant,UUID empresa);
    @Query("select f.sucursalId from DevolucionVenta d join FacturaVenta f on f.id=d.facturaId and f.tenantId=d.tenantId and f.empresaId=d.empresaId where d.id=:id and d.tenantId=:tenant and d.empresaId=:empresa")
    Optional<UUID> sucursalFactura(@Param("id") UUID id,@Param("tenant") UUID tenant,@Param("empresa") UUID empresa);
    @Query("select d from DevolucionVenta d where d.facturaId=:factura and d.tenantId=:tenant and d.empresaId=:empresa and d.status='CONFIRMED' and (:todas=true or d.sucursalPosId is null or d.sucursalPosId in :sucursales) order by d.confirmadaEn desc,d.id desc")
    org.springframework.data.domain.Page<DevolucionVenta> buscarParaImpresion(@Param("factura") UUID factura,@Param("tenant") UUID tenant,@Param("empresa") UUID empresa,@Param("todas") boolean todas,@Param("sucursales") Collection<UUID> sucursales,org.springframework.data.domain.Pageable page);
    @Query("select count(d) from DevolucionVenta d where d.tenantId=:tenant and d.empresaId=:empresa and d.facturaId=:factura")
    long contarPorFactura(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("factura")UUID factura);
}
