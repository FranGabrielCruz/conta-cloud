package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.CotizacionReportData;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.stereotype.Service;
import java.io.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class CotizacionPdfService {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private volatile JasperReport compiledReport;

    public byte[] generar(CotizacionReportData data){try{return JasperExportManager.exportReportToPdf(
        JasperFillManager.fillReport(reporte(),parametros(data),new JRBeanCollectionDataSource(data.lineas())));
        }catch(JRException ex){throw new ReglaNegocioException("No fue posible generar el PDF de la cotización.",ex);}}

    static Map<String,Object> parametros(CotizacionReportData d){Map<String,Object> p=new HashMap<>();
        p.put("companyTradeName",valor(d.empresaNombreComercial()));p.put("companyLegalName",opcional(d.empresaRazonSocial()));
        p.put("companyTaxIdentification",PurchaseOrderPdfService.formatTaxIdentification(d.empresaIdentificacion()));
        p.put("companyAddress",opcional(d.empresaDireccion()));p.put("companyPhone",PurchaseOrderPdfService.formatPhone(d.empresaTelefono()));
        p.put("companyEmail",opcional(d.empresaCorreo()));p.put("companyLogo",d.empresaLogo()==null?null:new ByteArrayInputStream(d.empresaLogo()));
        p.put("quoteNumber",valor(d.numero()));p.put("quoteDate",formato(d.fecha()));p.put("validUntil",formato(d.validaHasta()));
        p.put("customerName",valor(d.clienteNombre()));p.put("customerTaxIdentification",valor(PurchaseOrderPdfService.formatTaxIdentification(d.clienteIdentificacion())));
        p.put("customerAddress",valor(d.clienteDireccion()));p.put("customerPhone",valor(PurchaseOrderPdfService.formatPhone(d.clienteTelefono())));
        p.put("customerEmail",opcional(d.clienteCorreo()));p.put("branchName",valor(d.sucursal()));p.put("currencyCode",valor(d.monedaCodigo()));
        p.put("reference",valor(d.referencia()));p.put("conditions",opcional(d.condiciones()));p.put("subtotal",d.subtotal());
        p.put("discount",d.descuento());p.put("tax",d.impuesto());p.put("total",d.total());
        p.put("totalLabel",vacio(d.monedaCodigo())?"TOTAL":"TOTAL "+d.monedaCodigo().trim().toUpperCase(Locale.ROOT));
        p.put("generatedBy",valor(d.generadoPor()));p.put("generatedAt",d.generadoEn()==null?"":DATE_TIME.format(d.generadoEn()));
        p.put("voided","ANULADA".equals(d.estado()));return p;}

    private JasperReport reporte() throws JRException{JasperReport result=compiledReport;if(result!=null)return result;
        synchronized(this){if(compiledReport==null){InputStream in=getClass().getResourceAsStream("/reports/sales-quote/sales-quote.jrxml");
            if(in==null)throw new ReglaNegocioException("No se encontró la plantilla de la cotización.");compiledReport=JasperCompileManager.compileReport(in);}return compiledReport;}}
    private static String formato(java.time.LocalDate date){return date==null?"—":DATE.format(date);}
    private static String valor(String value){return vacio(value)?"—":value.trim();}
    private static String opcional(String value){return vacio(value)?null:value.trim();}
    private static boolean vacio(String value){return value==null||value.isBlank();}
}
