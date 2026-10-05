package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.EstadoRecepcionCompra;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RecepcionCompraDto(UUID id,String numero,UUID proveedorId,String proveedor,UUID ordenCompraId,
        String ordenCompra,UUID facturaProveedorId,String facturaProveedor,UUID almacenId,String almacen,LocalDate fecha,String referencia,String notas,
        String motivoDiferencia, EstadoRecepcionCompra estado,long version,String motivoAnulacion,
        OffsetDateTime confirmadaEn, UUID confirmadaPor, List<LineaRecepcionCompraDto> lineas) {}
