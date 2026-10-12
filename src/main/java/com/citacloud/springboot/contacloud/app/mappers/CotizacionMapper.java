package com.citacloud.springboot.contacloud.app.mappers;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class CotizacionMapper {
    public CotizacionDto toDto(Cotizacion c){return base(c,c.getLineas().stream().map(this::linea).toList(),
        c.getEventos().stream().map(e->new EventoCotizacionDto(e.getTipo(),e.getDetalle(),e.getOcurridoEn())).toList());}
    public CotizacionDto toSummaryDto(Cotizacion c){return base(c,List.of(),List.of());}
    private CotizacionDto base(Cotizacion c,List<LineaCotizacionDto> lineas,List<EventoCotizacionDto> eventos){return new CotizacionDto(
        c.getId(),c.getNumero(),c.getClienteId(),c.getClienteNombre(),c.getClienteIdentificacion(),c.getClienteDireccion(),
        c.getClienteTelefono(),c.getClienteCorreo(),c.getSucursalId(),c.getSucursal()==null?null:c.getSucursal().getNombre(),
        c.getFecha(),c.getValidaHasta(),c.getMonedaId(),c.getMoneda()==null?null:c.getMoneda().getCodigoIso(),
        c.getMoneda()==null?null:c.getMoneda().getSimbolo(),c.getReferencia(),c.getCondiciones(),c.getSubtotal(),c.getDescuento(),
        c.getImpuesto(),c.getTotal(),c.getEstado(),c.getEstado(),c.getVersion(),c.getNotaAceptacion(),c.getMotivoRechazo(),
        c.getMotivoAnulacion(),c.getCotizacionOrigenId(),lineas,eventos);}
    private LineaCotizacionDto linea(LineaCotizacion l){return new LineaCotizacionDto(l.getId(),l.getNumeroLinea(),l.getProductoId(),
        l.getProductoCodigo(),l.getDescripcion(),l.getUnidadMedidaId(),l.getUnidadMedidaNombre(),l.getUnidadMedidaAbreviatura(),
        l.getCantidad(),l.getPrecioUnitario(),l.getTipoDescuento(),l.getValorDescuento(),l.getDescuento(),l.getImpuestoId(),
        l.getImpuestoNombre(),l.getTasaImpuesto(),l.getSubtotalBruto(),l.getBaseImponible(),l.getImpuesto(),l.getTotal());}
}
