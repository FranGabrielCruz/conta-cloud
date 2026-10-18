package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.TipoMetodoPagoVenta;
import java.util.UUID;
public record MetodoPagoPosDto(UUID id,String codigo,String nombre,TipoMetodoPagoVenta tipo,boolean requiereReferencia,
        boolean requiereCuentaBancaria,boolean permiteCambio,boolean efectivo,boolean electronico,String icono,boolean predeterminado){}
