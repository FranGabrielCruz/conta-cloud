package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record LineaRecepcionCompraInput(UUID lineaOrdenId,UUID lineaFacturaId,UUID productoId,BigDecimal cantidad) {}
