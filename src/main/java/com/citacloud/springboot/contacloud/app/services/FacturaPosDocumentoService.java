package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ArchivoPdfDto;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.reports.pos.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.*;

/** Maps stored monetary values and historical labels. Does not perform accounting calculations. */
@Service
public class FacturaPosDocumentoService {
    private final EmpresaRepository empresas; private final DatosEmpresaRepository datos;
    private final LineaFacturaVentaRepository lineas; private final PagoVentaRepository pagos;
    private final AplicacionPagoVentaRepository aplicaciones;
    private final PosInvoice80mmPdfRenderer renderer = new PosInvoice80mmPdfRenderer();
    public FacturaPosDocumentoService(EmpresaRepository empresas, DatosEmpresaRepository datos,
            LineaFacturaVentaRepository lineas, PagoVentaRepository pagos, AplicacionPagoVentaRepository aplicaciones) {
        this.empresas=empresas;this.datos=datos;this.lineas=lineas;this.pagos=pagos;this.aplicaciones=aplicaciones;
    }
    public void conservarEmisor(FacturaVenta factura, String caja) {
        verificarContexto(factura);
        factura.conservarEmisorPos(emisorActual(factura, caja));
    }
    private EmisorFacturaPos emisorActual(FacturaVenta f, String caja) {
        Empresa e=empresas.findByIdAndTenantId(f.getEmpresaId(),f.getTenantId()).orElseThrow();
        DatosEmpresa d=datos.findByEmpresaId(f.getEmpresaId()).orElse(null);
        return new EmisorFacturaPos(d==null||d.getNombreComercial()==null||d.getNombreComercial().isBlank()?e.getNombre():d.getNombreComercial(),
                e.getIdentificacionFiscal(), d==null?null:d.getDireccion(), d==null?null:d.getTelefono(),
                e.getPaisCodigo()==null?"XX":e.getPaisCodigo(), caja, e.getZonaHoraria());
    }
    public ArchivoPdfDto generar(FacturaVenta f) {
        try { return new ArchivoPdfDto("Factura_"+InvoiceFormats.fileName(f.getNumero()), renderer.render(documento(f))); }
        catch (InvoicePrintFailure|IllegalArgumentException ex) { throw new ReglaNegocioException("No fue posible generar el ticket de la factura.",ex); }
    }
    PosInvoiceDocument documento(FacturaVenta f) {
        verificarContexto(f);
        boolean legacy=f.getEmisorPos()==null;
        // Compatibility is explicit: the old schema did not preserve the original issuer.
        EmisorFacturaPos e=legacy?emisorActual(f,null):f.getEmisorPos();
        List<LineaFacturaVenta> detalle=lineas.findAllByFacturaIdAndTenantIdAndEmpresaIdOrderByOrden(f.getId(),f.getTenantId(),f.getEmpresaId());
        Map<String,BigDecimal> impuestos=new LinkedHashMap<>();
        for(var l:detalle) {
            if(l.getImpuesto()!=null&&l.getImpuesto().signum()!=0) {
                String nombre=l.getImpuestoNombre()==null||l.getImpuestoNombre().isBlank()?"Impuestos":l.getImpuestoNombre();
                impuestos.merge(nombre,l.getImpuesto(),BigDecimal::add);
            }
        }
        if(impuestos.isEmpty())impuestos.put("Impuestos",f.getImpuestoTotal());
        List<PosInvoiceDocument.AmountLine> medios=pagos.findByFacturaIdAndTenantIdAndEmpresaId(f.getId(),f.getTenantId(),f.getEmpresaId())
                .map(p->aplicaciones.findAllByPagoIdAndTenantIdAndEmpresaIdOrderByLineOrder(p.getId(),f.getTenantId(),f.getEmpresaId()).stream()
                .map(a->new PosInvoiceDocument.AmountLine(a.getNombreMetodo(),a.getMonto())).toList()).orElse(List.of());
        List<String> footer=new ArrayList<>();footer.add("Gracias por su compra");footer.add("Conserve este comprobante");
        if(f.getNota()!=null&&!f.getNota().isBlank()) {
            String note=f.getNota();for(int i=0;i<note.length();i+=300)footer.add(note.substring(i,Math.min(i+300,note.length())));
        }
        if(legacy)footer.add("Emisor actual: esta factura no conserva su identidad histórica.");
        var fecha=e.getZona()==null?f.getConfirmadaEn().toLocalDateTime():f.getConfirmadaEn().atZoneSameInstant(ZoneId.of(e.getZona())).toLocalDateTime();
        // No fiscal type/NCF is stored by the current sales_invoice schema. Never invent either.
        return new PosInvoiceDocument(new PosInvoiceDocument.Issuer(e.getNombre(),e.getIdentificacion(),e.getDireccion(),e.getTelefono(),e.getPais()),
                "FACTURA",null,f.getNumero(),fecha,e.getCaja(),f.getVendedorNombre(),new PosInvoiceDocument.Customer(f.getClienteNombre(),f.getClienteIdentificacion()),
                detalle.stream().map(l->new PosInvoiceDocument.Item(l.getCantidad(),l.getDescripcion(),l.getTotal())).toList(),
                f.getSubtotal(),f.getDescuentoTotal(),impuestos.entrySet().stream().map(t->new PosInvoiceDocument.AmountLine(t.getKey(),t.getValue())).toList(),
                f.getTotal(),f.getMonedaCodigo(),medios,f.getCambio(),footer,false,f.getCondicionPago(),f.getRecargoTotal(),f.getRecibido());
    }
    private static void verificarContexto(FacturaVenta f) {
        var p=TenantContext.principalActual();
        if(!p.tenantId().equals(f.getTenantId())||!p.empresaId().equals(f.getEmpresaId())||!com.citacloud.springboot.contacloud.app.security.EmpresaContext.permiteSucursal(f.getSucursalId()))throw new RecursoNoEncontradoException("Factura no encontrada.");
    }
}
