package com.citacloud.springboot.contacloud.app.mappers;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import org.springframework.stereotype.Component;

@Component
public class OrdenCompraMapper {
    public OrdenCompraDto toDto(OrdenCompra o){return base(o,o.getLineas().stream().map(this::linea).toList());}
    public OrdenCompraDto toSummaryDto(OrdenCompra o){return base(o,java.util.List.of());}
    private OrdenCompraDto base(OrdenCompra o,java.util.List<LineaOrdenCompraDto> lineas){return new OrdenCompraDto(o.getId(),o.getNumero(),o.getProveedorId(),
        o.getProveedorNombre(),o.getProveedorIdentificacion(),o.getSucursalId(),o.getSucursal()==null?null:o.getSucursal().getNombre(),
        o.getFecha(),o.getFechaEntrega(),o.getMonedaId(),o.getMoneda()==null?null:o.getMoneda().getCodigoIso(),
        o.getCondicionPagoId(),o.getCondicionPago()==null?null:o.getCondicionPago().getNombre(),o.getReferencia(),o.getNotas(),
        o.getSubtotal(),o.getDescuento(),o.getImpuesto(),o.getTotal(),o.getEstado(),o.getVersion(),o.getMotivoAnulacion(),
        o.getEmitidaEn(),o.getPdfObjectKey()!=null&&!o.getPdfObjectKey().isBlank(),lineas);}
    private LineaOrdenCompraDto linea(LineaOrdenCompra l){return new LineaOrdenCompraDto(l.getId(),l.getNumeroLinea(),l.getProductoId(),l.getProductoCodigo(),l.getUnidadMedidaId(),l.getUnidadMedidaNombre(),l.getUnidadMedidaAbreviatura(),
        l.getDescripcion(),l.getCantidad(),l.getPrecioUnitario(),l.getDescuento(),l.getImpuestoId(),l.getImpuestoNombre(),
        l.getTasaImpuesto(),l.getSubtotalBruto(),l.getBaseImponible(),l.getImpuesto(),l.getTotal());}
}
