package com.citacloud.springboot.contacloud.app.dto;

import java.math.BigDecimal;
import java.util.UUID;
import com.citacloud.springboot.contacloud.app.models.OrigenLineaRecepcion;
import com.citacloud.springboot.contacloud.app.models.TipoDiferenciaRecepcion;

public record LineaRecepcionCompraDto(UUID lineaOrdenId,UUID productoId,String codigo,String descripcion,
        String unidad,BigDecimal ordenada,BigDecimal recibidaAntes,BigDecimal cantidad,BigDecimal pendiente,
        BigDecimal exceso, OrigenLineaRecepcion origen, TipoDiferenciaRecepcion diferencia) {}
