package com.citacloud.springboot.contacloud.app.dto;
public record ConfiguracionDescuentoInput(boolean habilitado,boolean permiteVenta,boolean permiteLinea,boolean permitePorcentaje,boolean permiteMonto,boolean requiereMotivo,boolean permiteAutorizacion,long version){}
