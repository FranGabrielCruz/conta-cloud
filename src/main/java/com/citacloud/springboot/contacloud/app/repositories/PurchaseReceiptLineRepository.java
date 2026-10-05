package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.LineaRecepcionCompra;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.UUID;

public interface PurchaseReceiptLineRepository extends JpaRepository<LineaRecepcionCompra,UUID> {
    @Query("""
      select coalesce(sum(l.cantidad),0) from LineaRecepcionCompra l join l.recepcion r
      where l.lineaOrdenId=:linea and r.tenantId=:tenant and r.empresaId=:empresa
      and r.status=com.citacloud.springboot.contacloud.app.models.EstadoRecepcionCompra.CONFIRMED
      """)
    BigDecimal recibidoConfirmado(@Param("linea")UUID linea,@Param("tenant")UUID tenant,@Param("empresa")UUID empresa);
    @Query("""
      select coalesce(sum(l.cantidad),0) from LineaRecepcionCompra l join l.recepcion r
      where l.lineaFacturaId=:linea and r.tenantId=:tenant and r.empresaId=:empresa
      and r.status=com.citacloud.springboot.contacloud.app.models.EstadoRecepcionCompra.CONFIRMED
      """)
    BigDecimal recibidoConfirmadoFactura(@Param("linea")UUID linea,@Param("tenant")UUID tenant,@Param("empresa")UUID empresa);
}
