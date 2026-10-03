package com.citacloud.springboot.contacloud.app.mappers;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.Producto;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class ProductoMapper {
    public ProductoDto toDto(Producto p){return new ProductoDto(p.getId(),p.getCodigo(),p.getNombre(),p.getTipo(),p.getCategoriaId(),
        p.getCategoria()==null?null:p.getCategoria().getNombre(),p.getUnidadMedidaId(),
        p.getUnidadMedida()==null?null:p.getUnidadMedida().getNombre(),p.getUnidadMedida()==null?null:p.getUnidadMedida().getAbreviatura(),
        p.getCodigoBarras(),p.getDescripcion(),p.getCostoCompra(),p.getPrecioVenta(),p.getMonedaId(),
        p.getMoneda()==null?null:p.getMoneda().getCodigoIso(),p.getImpuestoCompraId(),
        p.getImpuestoCompra()==null?null:p.getImpuestoCompra().getNombre(),p.getImpuestoCompra()==null?null:p.getImpuestoCompra().getPorcentaje(),
        p.getImpuestoVentaId(),p.getImpuestoVenta()==null?null:p.getImpuestoVenta().getNombre(),
        p.getImpuestoVenta()==null?null:p.getImpuestoVenta().getPorcentaje(),p.isControlaExistencia(),
        p.isPermiteExistenciaNegativa(),p.getStockMinimo(),p.isActivo(),p.getVersion());}
    public Producto toEntity(ProductoInput i,UUID tenant,UUID empresa,String codigo,String nombre,String barcode,String descripcion,
            boolean track,boolean negative,java.math.BigDecimal minimum,UUID usuario){return new Producto(tenant,empresa,codigo,nombre,i.tipo(),
        i.categoriaId(),i.unidadMedidaId(),barcode,descripcion,i.costoCompra(),i.precioVenta(),i.monedaId(),i.impuestoCompraId(),
        i.impuestoVentaId(),track,negative,minimum,i.activo(),usuario);}
}
