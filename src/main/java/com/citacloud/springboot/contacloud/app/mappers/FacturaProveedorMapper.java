package com.citacloud.springboot.contacloud.app.mappers;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;

@Component
public class FacturaProveedorMapper {
    public FacturaProveedor toEntity(FacturaProveedorInput i,UUID tenant,UUID empresa,String numeroInterno,String normalizado,UUID usuario){
        return new FacturaProveedor(tenant,empresa,numeroInterno,i.proveedorId(),i.sucursalId(),i.numeroFactura(),normalizado,
            i.numeroFiscal(),i.fecha(),i.vencimiento(),i.condicionPagoId(),i.monedaId(),i.ordenCompraId(),
            i.referencia(),i.notas(),usuario);
    }
    public FacturaProveedorDto toDto(FacturaProveedor f,CuentaPagar c){return base(f,c,f.getLineas().stream().map(this::linea).toList());}
    public FacturaProveedorDto toSummaryDto(FacturaProveedor f,CuentaPagar c){return base(f,c,List.of());}
    private FacturaProveedorDto base(FacturaProveedor f,CuentaPagar c,List<LineaFacturaProveedorDto> lineas){
        return new FacturaProveedorDto(f.getId(),f.getProveedorId(),f.getProveedor()==null?null:f.getProveedor().getNombreComercial(),
            f.getSucursalId(),f.getSucursal()==null?null:f.getSucursal().getNombre(),f.getNumeroProveedor(),f.getNumeroFiscal(),
            f.getFecha(),f.getVencimiento(),f.getCondicionPagoId(),conditionName(f),
            f.getMonedaId(),f.getMoneda()==null?null:f.getMoneda().getCodigoIso(),f.getOrdenCompraId(),
            f.getOrdenCompra()==null?null:f.getOrdenCompra().getNumero(),f.getReferencia(),f.getNotas(),f.getSubtotal(),
            f.getDescuento(),f.getImpuesto(),f.getTotal(),f.getEstado(),f.getVersion(),f.getMotivoAnulacion(),
            c==null?null:c.saldo(),c==null?null:c.getEstado(),conditionType(f),conditionDays(f),
            f.getEstadoFinanciero(),f.getCondicionPagoTipoSnapshot()==TipoCondicionPago.CASH,lineas);
    }
    private static TipoCondicionPago conditionType(FacturaProveedor f){return f.getCondicionPagoTipoSnapshot()!=null
        ?f.getCondicionPagoTipoSnapshot():f.getCondicionPago()==null?null:f.getCondicionPago().getTipo();}
    private static String conditionName(FacturaProveedor f){return f.getCondicionPagoNombreSnapshot()!=null
        ?f.getCondicionPagoNombreSnapshot():f.getCondicionPago()==null?null:f.getCondicionPago().getNombre();}
    private static Integer conditionDays(FacturaProveedor f){return f.getCondicionPagoDiasSnapshot()!=null
        ?f.getCondicionPagoDiasSnapshot():f.getCondicionPago()==null?null:f.getCondicionPago().getDias();}
    private LineaFacturaProveedorDto linea(LineaFacturaProveedor l){return new LineaFacturaProveedorDto(l.getProductoId(),
        l.getProductoCodigo(),l.getDescripcion(),l.getUnidad(),l.getCantidad(),l.getPrecio(),l.getDescuento(),l.getImpuestoId(),
        l.getImpuestoNombre(),l.getImpuestoTasa(),l.getImpuesto(),l.getSubtotal(),l.getTotal());}
}
