package com.citacloud.springboot.contacloud.app.dto;

import java.util.List;

public record ResultadoCobroPosInput(boolean ventaCredito,List<AplicacionCobroInput> aplicaciones) {
    public ResultadoCobroPosInput {
        aplicaciones=aplicaciones==null?List.of():List.copyOf(aplicaciones);
    }
}
