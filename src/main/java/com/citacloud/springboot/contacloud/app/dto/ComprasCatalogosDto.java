package com.citacloud.springboot.contacloud.app.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.citacloud.springboot.contacloud.app.models.TipoCondicionPago;

public record ComprasCatalogosDto(List<Opcion> proveedores,List<Opcion> sucursales,List<Opcion> monedas,
        List<Opcion> condicionesPago,List<Opcion> almacenes,List<Opcion> ordenes,UUID monedaBaseId,
        UUID condicionPagoCreditoId,Map<UUID,UUID> condicionPagoProveedorIds,
        Map<UUID,CondicionPagoDetalle> detallesCondicionesPago) {
    public record Opcion(UUID id,String nombre) {}
    public record CondicionPagoDetalle(TipoCondicionPago tipo,int dias) {}
}
