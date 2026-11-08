package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.LineaDevolucionVenta;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.*;
public interface LineaDevolucionVentaRepository extends JpaRepository<LineaDevolucionVenta,UUID>{
    @Query("select coalesce(sum(l.cantidad),0) from LineaDevolucionVenta l where l.tenantId=:tenant and l.empresaId=:empresa and l.lineaFacturaId=:linea")
    BigDecimal cantidadDevuelta(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("linea")UUID lineaFactura);
    @Query(value="select coalesce(sum(credited_quantity),0) from sales_credit_note_line l where tenant_id=:tenant and empresa_id=:empresa and sales_invoice_line_id=:linea and exists(select 1 from sales_credit_note n where n.id=l.sales_credit_note_id and n.tenant_id=l.tenant_id and n.empresa_id=l.empresa_id and n.status='CONFIRMED')",nativeQuery=true)
    BigDecimal cantidadAcreditada(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("linea")UUID lineaFactura);
    List<LineaDevolucionVenta> findAllByDevolucionIdOrderByOrden(UUID devolucionId);
    List<LineaDevolucionVenta> findAllByDevolucionIdAndTenantIdAndEmpresaIdOrderByOrden(UUID devolucionId,UUID tenantId,UUID empresaId);
}
