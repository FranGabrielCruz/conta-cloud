package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.TipoProducto;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductoDto(UUID id,String codigo,String nombre,TipoProducto tipo,UUID categoriaId,String categoriaNombre,
    UUID unidadMedidaId,String unidadMedidaNombre,String unidadMedidaAbreviatura,String codigoBarras,String descripcion,
    BigDecimal costoCompra,BigDecimal precioVenta,UUID monedaId,String monedaCodigo,UUID impuestoCompraId,
    String impuestoCompraNombre,BigDecimal impuestoCompraTasa,UUID impuestoVentaId,String impuestoVentaNombre,
    BigDecimal impuestoVentaTasa,boolean controlaExistencia,boolean permiteExistenciaNegativa,BigDecimal stockMinimo,
    boolean activo,long version) {}
