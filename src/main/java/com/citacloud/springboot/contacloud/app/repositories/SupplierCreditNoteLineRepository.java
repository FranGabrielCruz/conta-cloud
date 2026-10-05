package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.LineaNotaCreditoProveedor;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;import java.math.BigDecimal;import java.util.UUID;
public interface SupplierCreditNoteLineRepository extends JpaRepository<LineaNotaCreditoProveedor,UUID>{
 @Query("select coalesce(sum(l.quantity),0) from LineaNotaCreditoProveedor l join l.nota n where l.lineaFacturaId=:linea and n.tenantId=:tenant and n.empresaId=:empresa and n.status<>com.citacloud.springboot.contacloud.app.models.EstadoNotaCreditoProveedor.DRAFT and n.status<>com.citacloud.springboot.contacloud.app.models.EstadoNotaCreditoProveedor.VOIDED") BigDecimal cantidadAcreditada(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("linea")UUID linea);
}
