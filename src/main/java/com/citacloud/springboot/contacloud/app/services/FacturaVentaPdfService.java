package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.security.EmpresaContext;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class FacturaVentaPdfService {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final FacturaVentaRepository facturas;private final LineaFacturaVentaRepository lineas;
    private final EmpresaRepository empresas;private final DatosEmpresaRepository datos;private final SucursalRepository sucursales;
    private final LocalFileStorageService storage;private volatile JasperReport report;
    public FacturaVentaPdfService(FacturaVentaRepository facturas,LineaFacturaVentaRepository lineas,EmpresaRepository empresas,
            DatosEmpresaRepository datos,SucursalRepository sucursales,LocalFileStorageService storage){this.facturas=facturas;this.lineas=lineas;
        this.empresas=empresas;this.datos=datos;this.sucursales=sucursales;this.storage=storage;}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('facturas.pdf')")
    public ArchivoPdfDto generar(UUID id){
        var p=TenantContext.principalActual();FacturaVenta f=facturas.findByIdAndTenantIdAndEmpresaId(id,p.tenantId(),p.empresaId())
            .orElseThrow(()->new RecursoNoEncontradoException("Factura no encontrada."));
        if(!EmpresaContext.permiteSucursal(f.getSucursalId()))throw new RecursoNoEncontradoException("Factura no encontrada.");
        Empresa e=empresas.findByIdAndTenantId(p.empresaId(),p.tenantId()).orElseThrow();DatosEmpresa d=datos.findByEmpresaId(p.empresaId()).orElse(null);
        String sucursal=sucursales.findByIdAndEmpresaId(f.getSucursalId(),p.empresaId()).map(Sucursal::getNombre).orElse("—");
        byte[] logo=e.getLogoObjectKey()==null?null:storage.load(e.getLogoObjectKey(),5L*1024*1024).orElse(null);
        List<FacturaVentaReporteLinea> detalle=new ArrayList<>();int n=1;
        for(LineaFacturaVenta l:lineas.findAllByFacturaIdAndTenantIdAndEmpresaIdOrderByOrden(f.getId(),p.tenantId(),p.empresaId()))detalle.add(new FacturaVentaReporteLinea(n++,l.getCodigoProducto(),
            l.getDescripcion(),l.getUnidadNombre(),l.getCantidad(),l.getPrecioUnitario(),l.getImpuesto(),l.getTotal()));
        Map<String,Object> x=new HashMap<>();
        x.put("companyName",d==null?e.getNombre():primero(d.getNombreComercial(),e.getNombre()));x.put("companyLegalName",d==null?null:d.getRazonSocial());
        x.put("companyTaxId",PurchaseOrderPdfService.formatTaxIdentification(e.getIdentificacionFiscal()));x.put("companyAddress",d==null?null:d.getDireccion());
        x.put("companyPhone",d==null?null:PurchaseOrderPdfService.formatPhone(d.getTelefono()));x.put("companyEmail",d==null?null:d.getCorreo());
        x.put("companyLogo",logo==null?null:new ByteArrayInputStream(logo));x.put("number",f.getNumero());x.put("date",DATE.format(f.getFecha()));
        x.put("confirmedAt",DATE_TIME.format(f.getConfirmadaEn()));x.put("customerName",f.getClienteNombre());
        x.put("customerId",valor(f.getClienteIdentificacion()));x.put("seller",f.getVendedorNombre());x.put("branch",sucursal);
        x.put("currency",f.getMonedaCodigo());x.put("paymentCondition",f.getCondicionPago());
        x.put("dueDate",f.getVencimiento()==null?null:DATE.format(f.getVencimiento()));x.put("notes",f.getNota());
        x.put("status",f.getStatus().name());
        x.put("subtotal",f.getSubtotal());x.put("discount",f.getDescuentoTotal());x.put("surcharge",f.getRecargoTotal());x.put("tax",f.getImpuestoTotal());x.put("total",f.getTotal());x.put("received",f.getRecibido());x.put("change",f.getCambio());
        try{return new ArchivoPdfDto("Factura_"+f.getNumero()+".pdf",JasperExportManager.exportReportToPdf(
            JasperFillManager.fillReport(reporte(),x,new JRBeanCollectionDataSource(detalle))));}
        catch(JRException ex){throw new ReglaNegocioException("No fue posible generar el PDF de la factura.",ex);}
    }
    private JasperReport reporte() throws JRException{JasperReport r=report;if(r!=null)return r;synchronized(this){if(report==null){
        InputStream in=getClass().getResourceAsStream("/reports/sales-invoice/sales-invoice.jrxml");if(in==null)throw new ReglaNegocioException("No se encontró la plantilla de factura.");report=JasperCompileManager.compileReport(in);}return report;}}
    private static String primero(String a,String b){return a==null||a.isBlank()?b:a.trim();}private static String valor(String v){return v==null||v.isBlank()?"—":v;}
}
