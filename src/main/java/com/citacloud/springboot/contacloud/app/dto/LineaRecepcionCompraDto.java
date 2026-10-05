package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.util.UUID;
import com.citacloud.springboot.contacloud.app.models.OrigenLineaRecepcion;
import com.citacloud.springboot.contacloud.app.models.TipoDiferenciaRecepcion;

public record LineaRecepcionCompraDto(UUID lineaOrdenId,UUID lineaFacturaId,UUID productoId,String codigo,String descripcion,
        String unidad,BigDecimal ordenada,BigDecimal facturada,BigDecimal recibidaAntesOrden,
        BigDecimal recibidaAntesFactura,BigDecimal cantidad,BigDecimal pendienteOrden,BigDecimal pendienteFactura,
        BigDecimal excesoOrden,BigDecimal excesoFactura, OrigenLineaRecepcion origen, TipoDiferenciaRecepcion diferencia) {}
