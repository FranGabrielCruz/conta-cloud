package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.EstadoFacturaVenta;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record FacturaVentaResumenDto(UUID id,String numero,OffsetDateTime fechaHora,String cliente,
        BigDecimal total,String moneda,EstadoFacturaVenta estado) {}
