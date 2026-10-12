package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.EstadoCotizacion;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public record CotizacionDto(UUID id,String numero,UUID clienteId,String clienteNombre,String clienteIdentificacion,
        String clienteDireccion,String clienteTelefono,String clienteCorreo,UUID sucursalId,String sucursalNombre,
        LocalDate fecha,LocalDate validaHasta,UUID monedaId,String monedaCodigo,String monedaSimbolo,String referencia,
        String condiciones,BigDecimal subtotal,BigDecimal descuento,BigDecimal impuesto,BigDecimal total,
        EstadoCotizacion estado,EstadoCotizacion estadoRegistrado,long version,String notaAceptacion,String motivoRechazo,
        String motivoAnulacion,UUID cotizacionOrigenId,List<LineaCotizacionDto> lineas,List<EventoCotizacionDto> eventos) {}
