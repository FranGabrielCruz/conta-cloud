package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.TipoProducto;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductoInput(String nombre,TipoProducto tipo,UUID categoriaId,UUID unidadMedidaId,String codigoBarras,
    String descripcion,BigDecimal costoCompra,BigDecimal precioVenta,UUID monedaId,UUID impuestoCompraId,
    UUID impuestoVentaId,boolean controlaExistencia,boolean permiteExistenciaNegativa,BigDecimal stockMinimo,
    boolean activo,Long version) {}
