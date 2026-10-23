package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class DevolucionVentaPdfService {
    private static final DateTimeFormatter DATE_TIME=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final DevolucionVentaRepository devoluciones; private final LineaDevolucionVentaRepository lineas;
    private final NotaCreditoVentaRepository notas; private final EmpresaRepository empresas;
    private final DatosEmpresaRepository datos; private final LocalFileStorageService storage;
    private volatile JasperReport report;

    public DevolucionVentaPdfService(DevolucionVentaRepository devoluciones,LineaDevolucionVentaRepository lineas,
            NotaCreditoVentaRepository notas,EmpresaRepository empresas,DatosEmpresaRepository datos,LocalFileStorageService storage){
        this.devoluciones=devoluciones;this.lineas=lineas;this.notas=notas;this.empresas=empresas;this.datos=datos;this.storage=storage;
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('devoluciones_venta.pdf')")
    public ArchivoPdfDto generar(UUID id){
        var p=TenantContext.principalActual();
        DevolucionVenta d=devoluciones.findByIdAndTenantIdAndEmpresaId(id,p.tenantId(),p.empresaId())
                .orElseThrow(()->new RecursoNoEncontradoException("Devolución no encontrada."));
        NotaCreditoVenta nota=notas.findByDevolucionIdAndTenantIdAndEmpresaId(id,p.tenantId(),p.empresaId()).orElseThrow();
        Empresa empresa=empresas.findByIdAndTenantId(p.empresaId(),p.tenantId()).orElseThrow();
        DatosEmpresa dato=datos.findByEmpresaId(p.empresaId()).orElse(null);
        byte[] logo=empresa.getLogoObjectKey()==null?null:storage.load(empresa.getLogoObjectKey(),5L*1024*1024).orElse(null);
        List<DevolucionVentaReporteLinea> detalle=new ArrayList<>();int orden=1;
        for(LineaDevolucionVenta l:lineas.findAllByDevolucionIdAndTenantIdAndEmpresaIdOrderByOrden(id,p.tenantId(),p.empresaId()))
            detalle.add(new DevolucionVentaReporteLinea(orden++,l.getProductoCodigo(),l.getDescripcion(),l.getUnidadNombre(),l.getCantidad(),l.getPrecio(),l.getImpuesto(),l.getTotal()));
        Map<String,Object>x=new HashMap<>();
        x.put("companyName",dato==null?empresa.getNombre():primero(dato.getNombreComercial(),empresa.getNombre()));
        x.put("companyTaxId",PurchaseOrderPdfService.formatTaxIdentification(empresa.getIdentificacionFiscal()));
        x.put("companyPhone",dato==null?null:PurchaseOrderPdfService.formatPhone(dato.getTelefono()));
        x.put("companyLogo",logo==null?null:new ByteArrayInputStream(logo));x.put("returnNumber",d.getNumero());
        x.put("creditNoteNumber",nota.getNumero());x.put("invoiceNumber",d.getFacturaNumero());x.put("confirmedAt",DATE_TIME.format(d.getConfirmadaEn()));
        x.put("customerName",d.getClienteNombre());x.put("customerId",valor(d.getClienteIdentificacion()));x.put("reason",d.getMotivoNombre());
        x.put("observation",d.getObservacion());x.put("inventory",d.isRetornaInventario()?"Sí":"No");x.put("warehouse",valor(d.getAlmacenDestinoNombre()));
        x.put("currency",d.getMonedaCodigo());x.put("subtotal",d.getSubtotalBruto());x.put("discount",d.getDescuento());
        x.put("surcharge",d.getRecargo());x.put("tax",d.getImpuesto());x.put("total",d.getTotal());
        try{return new ArchivoPdfDto("Devolucion_"+d.getNumero()+".pdf",JasperExportManager.exportReportToPdf(
                JasperFillManager.fillReport(reporte(),x,new JRBeanCollectionDataSource(detalle))));}
        catch(JRException ex){throw new ReglaNegocioException("La devolución fue confirmada, pero no fue posible generar su PDF.",ex);}
    }

    private JasperReport reporte()throws JRException{JasperReport r=report;if(r!=null)return r;synchronized(this){if(report==null){
        InputStream in=getClass().getResourceAsStream("/reports/sales-return/sales-return.jrxml");
        if(in==null)throw new ReglaNegocioException("No se encontró la plantilla de devolución.");report=JasperCompileManager.compileReport(in);}return report;}}
    private static String primero(String a,String b){return a==null||a.isBlank()?b:a.trim();}
    private static String valor(String v){return v==null||v.isBlank()?"—":v;}
}
