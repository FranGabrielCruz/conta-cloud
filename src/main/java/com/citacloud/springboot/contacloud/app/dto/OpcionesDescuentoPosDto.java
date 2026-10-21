package com.citacloud.springboot.contacloud.app.dto;
import java.util.List;
public record OpcionesDescuentoPosDto(boolean permiteVenta,boolean permiteLinea,boolean permitePorcentaje,boolean permiteMonto,boolean requiereMotivo,boolean permiteAutorizacion,String moneda,List<ConfiguracionDescuentoDto.Motivo> motivos){}
