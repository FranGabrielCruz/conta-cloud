package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ArchivoPdfDto;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.reports.pos.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.*;

/** Stored return data only. Capturing identity never renders, queues or prints anything. */
@Service
public class DevolucionPosDocumentoService {
    private final DevolucionVentaRepository devoluciones;private final FacturaVentaRepository facturas;
    private final LineaDevolucionVentaRepository lineas;private final NotaCreditoVentaRepository notas;
    private final EmpresaRepository empresas;private final DatosEmpresaRepository datos;
    private final CashRegisterRepository cajas;private final SucursalRepository sucursales;private final TerminalPosRepository terminales;
    private final SalesReturn80mmPdfRenderer renderer=new SalesReturn80mmPdfRenderer();
    public DevolucionPosDocumentoService(DevolucionVentaRepository devoluciones,FacturaVentaRepository facturas,
            LineaDevolucionVentaRepository lineas,NotaCreditoVentaRepository notas,EmpresaRepository empresas,DatosEmpresaRepository datos,
            CashRegisterRepository cajas,SucursalRepository sucursales,TerminalPosRepository terminales){
        this.devoluciones=devoluciones;this.facturas=facturas;this.lineas=lineas;this.notas=notas;this.empresas=empresas;this.datos=datos;this.cajas=cajas;this.sucursales=sucursales;this.terminales=terminales;
    }
    public void conservarIdentidad(DevolucionVenta d,FacturaVenta f,String terminalToken){
        var p=TenantContext.principalActual();verificarTenant(d);
        if(!p.tenantId().equals(f.getTenantId())||!p.empresaId().equals(f.getEmpresaId())||!EmpresaContext.permiteSucursal(f.getSucursalId()))throw noEncontrada();
        UUID cajaId=f.getCajaId();
        if(terminalToken!=null){
            if(terminalToken.isBlank()||terminalToken.length()>512)throw new ReglaNegocioException("La terminal de devolución no está disponible.");
            String hash;
            try{hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(terminalToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
            catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
            cajaId=terminales.findByTokenHashAndTenantIdAndEmpresaIdAndActivoTrue(hash,p.tenantId(),p.empresaId())
                    .orElseThrow(()->new ReglaNegocioException("La terminal de devolución no está disponible.")).getCajaId();
        }
        Caja caja=cajas.findByIdAndTenantIdAndEmpresaId(cajaId,p.tenantId(),p.empresaId())
                .filter(c->EmpresaContext.permiteSucursal(c.getSucursalId())).orElseThrow(DevolucionPosDocumentoService::noEncontrada);
        String sucursal=sucursales.findByIdAndEmpresaId(caja.getSucursalId(),p.empresaId()).map(Sucursal::getNombre).orElse(null);
        d.conservarIdentidadPos(emisorActual(caja.getNombre()),caja.getSucursalId(),caja.getId(),sucursal,p.nombre());
    }
    private EmisorFacturaPos emisorActual(String caja){
        var p=TenantContext.principalActual();Empresa e=empresas.findByIdAndTenantId(p.empresaId(),p.tenantId()).orElseThrow(DevolucionPosDocumentoService::noEncontrada);
        DatosEmpresa dato=datos.findByEmpresaId(p.empresaId()).orElse(null);
        String nombre=dato==null||dato.getNombreComercial()==null||dato.getNombreComercial().isBlank()?e.getNombre():dato.getNombreComercial();
        return new EmisorFacturaPos(nombre,e.getIdentificacionFiscal(),dato==null?null:dato.getDireccion(),dato==null?null:dato.getTelefono(),e.getPaisCodigo()==null?"XX":e.getPaisCodigo(),caja,e.getZonaHoraria());
    }
    DevolucionVenta obtener(UUID id){
        var p=TenantContext.principalActual();var d=devoluciones.findByIdAndTenantIdAndEmpresaId(id,p.tenantId(),p.empresaId()).orElseThrow(DevolucionPosDocumentoService::noEncontrada);
        verificarTenant(d);if(!"CONFIRMED".equals(d.getStatus()))throw new ReglaNegocioException("La devolución no está confirmada.");
        origen(d);return d;
    }
    Origen origen(DevolucionVenta d){
        verificarTenant(d);
        UUID sucursal=d.getSucursalPosId(),caja=d.getCajaPosId();
        if(sucursal==null||caja==null){var f=factura(d);sucursal=f.getSucursalId();caja=f.getCajaId();}
        if(!EmpresaContext.permiteSucursal(sucursal))throw noEncontrada();return new Origen(sucursal,caja);
    }
    private FacturaVenta factura(DevolucionVenta d){var p=TenantContext.principalActual();return facturas.findByIdAndTenantIdAndEmpresaId(d.getFacturaId(),p.tenantId(),p.empresaId()).orElseThrow(DevolucionPosDocumentoService::noEncontrada);}
    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('devoluciones_venta.pdf')")
    public ArchivoPdfDto generar(UUID id){
        try{var d=obtener(id);return new ArchivoPdfDto("Devolucion_"+InvoiceFormats.fileName(d.getNumero()),renderer.render(documento(d)));}
        catch(InvoicePrintFailure|IllegalArgumentException ex){
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("event=SalesReturnRenderFailed returnId={} traceId={}",id,com.citacloud.springboot.contacloud.app.errors.TraceContext.currentOrCreate());
            throw new ReglaNegocioException("La devolución está confirmada, pero no fue posible generar su ticket.",ex);
        }
    }
    SalesReturnPrintDocument documento(DevolucionVenta d){
        origen(d);var p=TenantContext.principalActual();EmisorFacturaPos e=d.getEmisorPos();List<String> footer=new ArrayList<>(List.of("Documento de devolución","Conserve este comprobante"));
        if(e==null){e=factura(d).getEmisorPos();if(e==null){e=emisorActual(null);footer.add("Emisor actual: esta devolución no conserva su identidad histórica.");}else footer.add("Emisor histórico de la factura origen.");}
        var detalle=lineas.findAllByDevolucionIdAndTenantIdAndEmpresaIdOrderByOrden(d.getId(),p.tenantId(),p.empresaId());
        Map<String,BigDecimal> impuestos=new LinkedHashMap<>();
        for(var l:detalle)if(l.getImpuesto()!=null&&l.getImpuesto().signum()!=0)impuestos.merge(l.getImpuestoNombre()==null||l.getImpuestoNombre().isBlank()?"Impuestos":l.getImpuestoNombre(),l.getImpuesto(),BigDecimal::add);
        if(impuestos.isEmpty())impuestos.put("Impuestos",d.getImpuesto());
        NotaCreditoVenta nota=notas.findByDevolucionIdAndTenantIdAndEmpresaId(d.getId(),p.tenantId(),p.empresaId()).orElseThrow(DevolucionPosDocumentoService::noEncontrada);
        List<SalesReturnPrintDocument.AmountLine> reembolso=new ArrayList<>();
        if(nota.getAplicadoCxC()!=null&&nota.getAplicadoCxC().signum()>0)reembolso.add(new SalesReturnPrintDocument.AmountLine("Aplicado a cuenta por cobrar",nota.getAplicadoCxC()));
        if(nota.getCreditoCliente()!=null&&nota.getCreditoCliente().signum()>0)reembolso.add(new SalesReturnPrintDocument.AmountLine("Saldo a favor",nota.getCreditoCliente()));
        if(reembolso.isEmpty())reembolso.add(new SalesReturnPrintDocument.AmountLine("Nota de crédito",nota.getMonto()));
        String motivo=d.getMotivoNombre();if(d.getObservacion()!=null&&!d.getObservacion().isBlank())motivo=motivo+"\n"+d.getObservacion();
        var fecha=e.getZona()==null?d.getConfirmadaEn().toLocalDateTime():d.getConfirmadaEn().atZoneSameInstant(ZoneId.of(e.getZona())).toLocalDateTime();
        return new SalesReturnPrintDocument(new SalesReturnPrintDocument.CompanySnapshot(e.getNombre(),e.getIdentificacion(),e.getDireccion(),e.getTelefono(),e.getPais()),
            d.getNumero(),d.getFacturaNumero(),fecha,new SalesReturnPrintDocument.CustomerSnapshot(d.getClienteNombre(),d.getClienteIdentificacion()),d.getSucursalPosNombre(),e.getCaja(),d.getOperadorPosNombre(),motivo,d.getMonedaCodigo(),
            detalle.stream().map(l->new SalesReturnPrintDocument.ReturnedItem(l.getCantidad(),l.getDescripcion(),l.getTotal(),l.getPrecio(),l.getDescuento(),l.getImpuesto())).toList(),
            d.getSubtotalBruto(),d.getDescuento(),d.getRecargo(),impuestos.entrySet().stream().map(t->new SalesReturnPrintDocument.AmountLine(t.getKey(),t.getValue())).toList(),
            d.getTotal(),reembolso,reembolso.size()>1?nota.getMonto():null,footer,false);
    }
    private static void verificarTenant(DevolucionVenta d){var p=TenantContext.principalActual();if(!p.tenantId().equals(d.getTenantId())||!p.empresaId().equals(d.getEmpresaId()))throw noEncontrada();}
    private static RecursoNoEncontradoException noEncontrada(){return new RecursoNoEncontradoException("Devolución no encontrada.");}
    record Origen(UUID sucursalId,UUID cajaId){}
}
