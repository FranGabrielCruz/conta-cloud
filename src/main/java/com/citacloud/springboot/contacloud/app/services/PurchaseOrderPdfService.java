package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.PurchaseOrderReportData;
import com.citacloud.springboot.contacloud.app.models.EstadoOrdenCompra;
import com.citacloud.springboot.contacloud.app.repositories.PurchaseOrderRepository;
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
public class PurchaseOrderPdfService {
    private static final long MAX_PDF_BYTES=20L*1024*1024;
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final LocalFileStorageService storage;
    private final PurchaseOrderRepository orders;
    private volatile JasperReport compiledReport;

    public PurchaseOrderPdfService(LocalFileStorageService storage,PurchaseOrderRepository orders){this.storage=storage;this.orders=orders;}

    public String objectKey(PurchaseOrderReportData data){
        return "tenants/"+data.tenantId()+"/companies/"+data.companyId()+"/purchase-orders/"+data.orderDate().getYear()+"/"+data.orderId()+".pdf";
    }

    public byte[] generate(PurchaseOrderReportData data){
        try{
            Map<String,Object> p=parameters(data);
            JasperPrint print=JasperFillManager.fillReport(report(),p,new JRBeanCollectionDataSource(data.lines()));
            return JasperExportManager.exportReportToPdf(print);
        }catch(JRException ex){throw new ReglaNegocioException("No fue posible generar el PDF oficial de la orden de compra.",ex);}
    }

    static Map<String,Object> parameters(PurchaseOrderReportData data){
        Map<String,Object> p=new HashMap<>();
        p.put("companyTradeName",value(data.companyTradeName()));p.put("companyLegalName",optional(data.companyLegalName()));
        p.put("companyTaxIdentification",optional(data.companyTaxIdentification()));p.put("companyAddress",optional(data.companyAddress()));
        p.put("companyPhone",optional(data.companyPhone()));p.put("companyEmail",optional(data.companyEmail()));
        p.put("companyLogo",data.companyLogo()==null?null:new ByteArrayInputStream(data.companyLogo()));p.put("orderNumber",value(data.orderNumber()));
        p.put("supplierName",value(data.supplierName()));p.put("supplierTaxIdentification",value(data.supplierTaxIdentification()));
        p.put("supplierContactName",value(data.supplierContactName()));p.put("supplierPhone",value(data.supplierPhone()));
        p.put("supplierEmail",optional(data.supplierEmail()));p.put("orderDate",format(data.orderDate()));
        p.put("expectedDeliveryDate",format(data.expectedDeliveryDate()));p.put("branchName",value(data.branchName()));
        p.put("currencyCode",value(data.currencyCode()));p.put("paymentTermName",value(data.paymentTermName()));
        p.put("reference",value(data.reference()));p.put("notes",optional(data.notes()));p.put("subtotal",data.subtotal());
        p.put("discount",data.discount());p.put("tax",data.tax());p.put("total",data.total());
        p.put("totalLabel",blank(data.currencyCode())?"TOTAL":"TOTAL "+data.currencyCode().trim().toUpperCase(Locale.ROOT));
        p.put("issuedByDisplayName",value(data.issuedByDisplayName()));p.put("issuedAt",data.issuedAt()==null?"":DATE_TIME.format(data.issuedAt()));
        return p;
    }

    public String store(PurchaseOrderReportData data,byte[] pdf){return storage.storePdf(objectKey(data),pdf);}
    public void discard(String key){if(key!=null)storage.delete(key);}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('ordenes_compra.ver')")
    public byte[] getOfficialPdf(UUID orderId){
        var principal=TenantContext.principalActual();
        var order=orders.findByIdAndTenantIdAndEmpresaId(orderId,principal.tenantId(),principal.empresaId())
            .orElseThrow(()->new RecursoNoEncontradoException("Orden de compra no encontrada."));
        if(order.getEstado()==EstadoOrdenCompra.DRAFT||order.getPdfObjectKey()==null)
            throw new ReglaNegocioException("La orden de compra todavía no tiene un PDF oficial.");
        String expected="tenants/"+principal.tenantId()+"/companies/"+principal.empresaId()+"/purchase-orders/"+order.getFecha().getYear()+"/"+order.getId()+".pdf";
        if(!expected.equals(order.getPdfObjectKey()))throw new ReglaNegocioException("La referencia del PDF oficial no es válida.");
        return storage.load(expected,MAX_PDF_BYTES).orElseThrow(()->new RecursoNoEncontradoException("No se encontró el PDF oficial de la orden de compra."));
    }

    private JasperReport report() throws JRException{
        JasperReport result=compiledReport;if(result!=null)return result;
        synchronized(this){if(compiledReport==null){InputStream in=getClass().getResourceAsStream("/reports/purchase-order/purchase-order.jrxml");if(in==null)throw new ReglaNegocioException("No se encontró la plantilla de la orden de compra.");compiledReport=JasperCompileManager.compileReport(in);}return compiledReport;}
    }
    private static String format(java.time.LocalDate date){return date==null?"—":DATE.format(date);}
    private static String value(String value){return value==null||value.isBlank()?"—":value;}
    private static String optional(String value){return blank(value)?null:value.trim();}
    private static boolean blank(String value){return value==null||value.isBlank();}
}
