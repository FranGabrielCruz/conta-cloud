package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.TipoCondicionPago;
import java.util.List;
import java.util.UUID;

public record ClienteCatalogosDto(List<CondicionOpcion> condiciones,List<MonedaOpcion> monedas,
    List<ComprobanteOpcion> comprobantes,UUID monedaBaseId) {
    public record CondicionOpcion(UUID id,String nombre,TipoCondicionPago tipo,int dias) {}
    public record MonedaOpcion(UUID id,String codigo,String nombre,String simbolo) {}
    public record ComprobanteOpcion(UUID id,String codigo,String nombre) {}
}
