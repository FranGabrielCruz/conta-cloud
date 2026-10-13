package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.OrigenMovimientoInventario;import java.math.BigDecimal;import java.time.OffsetDateTime;import java.util.UUID;
public record MovimientoInventarioDetalleDto(UUID id,String numero,OffsetDateTime fechaEfectiva,OffsetDateTime registradoEn,String tipo,
    OrigenMovimientoInventario origen,String documento,UUID documentoId,String referencia,String estado,String producto,String productoCodigo,
    String unidad,String almacen,String sucursal,BigDecimal existenciaAnterior,BigDecimal entrada,BigDecimal salida,BigDecimal existenciaPosterior,
    BigDecimal cantidadOrigen,BigDecimal factorConversion,BigDecimal cantidadBase,String generadoPor,String proveedor,
    UUID movimientoOriginalId,String movimientoOriginalNumero,UUID movimientoReversionId,String movimientoReversionNumero,String tipoReferencia) {}
