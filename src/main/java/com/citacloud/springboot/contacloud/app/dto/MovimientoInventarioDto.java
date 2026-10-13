package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.OrigenMovimientoInventario;import java.math.BigDecimal;import java.time.OffsetDateTime;import java.util.UUID;
public record MovimientoInventarioDto(UUID id,String numero,OffsetDateTime fechaEfectiva,String documento,UUID documentoId,String referencia,
    UUID productoId,String productoCodigo,String producto,String unidad,UUID almacenId,String almacen,String sucursal,String tipo,
    OrigenMovimientoInventario origen,BigDecimal entrada,BigDecimal salida,BigDecimal existenciaPosterior,String estado) {}
