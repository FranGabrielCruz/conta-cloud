package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
public class UltimasFacturasService {
    public static final int TAMANO_PAGINA = 10;
    private static final UUID SUCURSAL_MARCADOR = new UUID(0L,0L);
    private final FacturaVentaRepository facturas;
    private final LineaFacturaVentaRepository lineas;
    private final PagoVentaRepository pagos;
    private final AplicacionPagoVentaRepository aplicaciones;

    public UltimasFacturasService(FacturaVentaRepository facturas,LineaFacturaVentaRepository lineas,
            PagoVentaRepository pagos,AplicacionPagoVentaRepository aplicaciones){
        this.facturas=facturas;this.lineas=lineas;this.pagos=pagos;this.aplicaciones=aplicaciones;
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('facturas.ver')")
    public Page<FacturaVentaResumenDto> buscar(String criterio,LocalDate desde,LocalDate hasta,
            EstadoFacturaVenta estado,int pagina){
        if(pagina<0)throw new ReglaNegocioException("La página solicitada no es válida.");
        if(desde!=null&&hasta!=null&&desde.isAfter(hasta))
            throw new ReglaNegocioException("La fecha inicial no puede ser posterior a la fecha final.");
        var principal=TenantContext.principalActual();
        String texto=limpiar(criterio).toLowerCase(Locale.ROOT);
        String identificacion=texto.replaceAll("\\D","");
        Collection<UUID> sucursales=principal.accesoTodasSucursales()?List.of(SUCURSAL_MARCADOR):principal.sucursalIds();
        if(!principal.accesoTodasSucursales()&&sucursales.isEmpty())return Page.empty(PageRequest.of(pagina,TAMANO_PAGINA));
        return facturas.buscarHistorico(principal.tenantId(),principal.empresaId(),principal.accesoTodasSucursales(),
                sucursales,texto,identificacion,desde,hasta,estado,
                PageRequest.of(pagina,TAMANO_PAGINA)).map(this::resumen);
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('facturas.ver')")
    public FacturaVentaDetalleDto detalle(UUID id){
        var principal=TenantContext.principalActual();
        FacturaVenta factura=facturas.findByIdAndTenantIdAndEmpresaId(id,principal.tenantId(),principal.empresaId())
                .orElseThrow(()->new RecursoNoEncontradoException("Factura no encontrada."));
        if(!EmpresaContext.permiteSucursal(factura.getSucursalId()))
            throw new RecursoNoEncontradoException("Factura no encontrada.");
        var detalle=lineas.findAllByFacturaIdAndTenantIdAndEmpresaIdOrderByOrden(id,principal.tenantId(),principal.empresaId())
                .stream().map(l->new FacturaVentaDetalleDto.Linea(l.getDescripcion(),l.getUnidadNombre(),l.getCantidad(),
                        l.getPrecioUnitario(),l.getImpuesto(),l.getTotal())).toList();
        List<FacturaVentaDetalleDto.Pago> cobros=pagos.findByFacturaIdAndTenantIdAndEmpresaId(id,principal.tenantId(),principal.empresaId())
                .map(p->aplicaciones.findAllByPagoIdAndTenantIdAndEmpresaIdOrderByLineOrder(p.getId(),principal.tenantId(),principal.empresaId())
                        .stream().map(a->new FacturaVentaDetalleDto.Pago(a.getNombreMetodo(),a.getMonto(),a.getRecibido(),
                                a.getCambio(),a.getReferencia())).toList()).orElseGet(List::of);
        return new FacturaVentaDetalleDto(factura.getId(),factura.getNumero(),factura.getConfirmadaEn(),factura.getClienteNombre(),
                factura.getClienteIdentificacion(),factura.getStatus(),factura.getMonedaCodigo(),factura.getCondicionPago(),
                factura.getVendedorNombre(),factura.getNota(),factura.getSubtotal(),factura.getDescuentoTotal(),
                factura.getRecargoTotal(),factura.getImpuestoTotal(),factura.getTotal(),detalle,cobros);
    }

    private FacturaVentaResumenDto resumen(FacturaVenta f){return new FacturaVentaResumenDto(f.getId(),f.getNumero(),
            f.getConfirmadaEn(),f.getClienteNombre(),f.getTotal(),f.getMonedaCodigo(),f.getStatus());}
    static String limpiar(String valor){if(valor==null)return "";String limpio=valor.trim();return limpio.length()>180?limpio.substring(0,180):limpio;}
}
