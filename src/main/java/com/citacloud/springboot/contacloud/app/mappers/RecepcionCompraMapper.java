package com.citacloud.springboot.contacloud.app.mappers;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;

@Component
public class RecepcionCompraMapper {
    public RecepcionCompra toEntity(RecepcionCompraInput i,UUID tenant,UUID empresa,String numero,UUID usuario){
        return new RecepcionCompra(tenant,empresa,numero,i.proveedorId(),i.ordenCompraId(),i.facturaProveedorId(),i.almacenId(),i.fecha(),
            i.referencia(),i.notas(),i.claveIdempotencia(),usuario);
    }
    public RecepcionCompraDto toDto(RecepcionCompra r,Map<UUID,BigDecimal> ordenadas,Map<UUID,BigDecimal> previasOrden,
            Map<UUID,BigDecimal> facturadas,Map<UUID,BigDecimal> previasFactura){
        return base(r,r.getLineas().stream().map(l->linea(l,ordenadas,previasOrden,facturadas,previasFactura)).toList());
    }
    public RecepcionCompraDto toSummaryDto(RecepcionCompra r){return base(r,List.of());}
    private RecepcionCompraDto base(RecepcionCompra r,List<LineaRecepcionCompraDto> lineas){return new RecepcionCompraDto(r.getId(),
        r.getNumero(),r.getProveedorId(),r.getProveedor()==null?null:r.getProveedor().getNombreComercial(),r.getOrdenCompraId(),
        r.getOrdenCompra()==null?null:r.getOrdenCompra().getNumero(),r.getFacturaProveedorId(),
        r.getFacturaProveedor()==null?null:r.getFacturaProveedor().getNumeroProveedor(),r.getAlmacenId(),r.getAlmacen()==null?null:r.getAlmacen().getNombre(),
        r.getFecha(),r.getReferencia(),r.getNotas(),r.getMotivoDiferencia(),r.getEstado(),r.getVersion(),
        r.getMotivoAnulacion(),r.getConfirmadaEn(),r.getConfirmadaPor(),lineas);}
    private LineaRecepcionCompraDto linea(LineaRecepcionCompra l,Map<UUID,BigDecimal> ordenadas,Map<UUID,BigDecimal> previasOrden,
            Map<UUID,BigDecimal> facturadas,Map<UUID,BigDecimal> previasFactura){
        BigDecimal ordered=l.getLineaOrdenId()==null?null:ordenadas.get(l.getLineaOrdenId());
        BigDecimal invoiced=l.getLineaFacturaId()==null?null:facturadas.get(l.getLineaFacturaId());
        BigDecimal priorOrder=l.getLineaOrdenId()==null?BigDecimal.ZERO:previasOrden.getOrDefault(l.getLineaOrdenId(),BigDecimal.ZERO);
        BigDecimal priorInvoice=l.getLineaFacturaId()==null?BigDecimal.ZERO:previasFactura.getOrDefault(l.getLineaFacturaId(),BigDecimal.ZERO);
        BigDecimal pendingOrder=ordered==null?null:ordered.subtract(priorOrder).subtract(l.getCantidad()).max(BigDecimal.ZERO);
        BigDecimal pendingInvoice=invoiced==null?null:invoiced.subtract(priorInvoice).subtract(l.getCantidad()).max(BigDecimal.ZERO);
        BigDecimal excessOrder=ordered==null?null:priorOrder.add(l.getCantidad()).subtract(ordered).max(BigDecimal.ZERO);
        BigDecimal excessInvoice=invoiced==null?null:priorInvoice.add(l.getCantidad()).subtract(invoiced).max(BigDecimal.ZERO);
        return new LineaRecepcionCompraDto(l.getLineaOrdenId(),l.getLineaFacturaId(),l.getProductoId(),l.getProductoCodigo(),l.getDescripcion(),
            l.getUnidad(),ordered,invoiced,priorOrder,priorInvoice,l.getCantidad(),pendingOrder,pendingInvoice,
            excessOrder,excessInvoice,l.getOrigen(),l.getDiferencia());
    }
}
