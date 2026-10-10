package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.SupplierPaymentReportData;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class SupplierPaymentPdfService {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private volatile JasperReport compiledReport;

    public byte[] generate(SupplierPaymentReportData data){
        try{
            JasperPrint print=JasperFillManager.fillReport(report(),parameters(data),
                new JRBeanCollectionDataSource(data.applications(),false));
            return JasperExportManager.exportReportToPdf(print);
        }catch(JRException ex){throw new ReglaNegocioException("No fue posible generar el comprobante de pago.",ex);}
    }

    static Map<String,Object> parameters(SupplierPaymentReportData data){
        Map<String,Object> p=new HashMap<>();
        p.put("companyTradeName",value(data.companyTradeName()));p.put("companyLegalName",optional(data.companyLegalName()));
        p.put("companyTaxIdentification",PurchaseOrderPdfService.formatTaxIdentification(data.companyTaxIdentification()));
        p.put("companyAddress",optional(data.companyAddress()));p.put("companyPhone",PurchaseOrderPdfService.formatPhone(data.companyPhone()));
        p.put("companyEmail",optional(data.companyEmail()));p.put("companyLogo",data.companyLogo()==null?null:new ByteArrayInputStream(data.companyLogo()));
        p.put("paymentNumber",value(data.paymentNumber()));p.put("paymentDate",data.paymentDate()==null?"—":DATE.format(data.paymentDate()));
        p.put("status",value(data.status()));p.put("supplierName",value(data.supplierName()));
        p.put("supplierTaxIdentification",value(PurchaseOrderPdfService.formatTaxIdentification(data.supplierTaxIdentification())));
        p.put("supplierPhone",value(PurchaseOrderPdfService.formatPhone(data.supplierPhone())));p.put("supplierEmail",value(data.supplierEmail()));
        p.put("currencyCode",value(data.currencyCode()));p.put("currencyPrefix",SupplierCreditNotePdfService.currencyPrefix(data.currencyCode()));
        p.put("paymentMethod",value(data.paymentMethod()));p.put("source",value(data.source()));p.put("checkNumber",optional(data.checkNumber()));
        p.put("reference",value(data.reference()));p.put("notes",optional(data.notes()));p.put("voidReason",optional(data.voidReason()));
        p.put("amount",data.amount());p.put("applied",data.applied());p.put("available",data.available());
        p.put("amountFormatted",money(data.amount(),data.currencyCode()));p.put("appliedFormatted",money(data.applied(),data.currencyCode()));p.put("availableFormatted",money(data.available(),data.currencyCode()));
        p.put("generatedByDisplayName",value(data.generatedByDisplayName()));p.put("generatedAt",data.generatedAt()==null?"":DATE_TIME.format(data.generatedAt()));
        p.put("hasApplications",!data.applications().isEmpty());
        return p;
    }

    private JasperReport report() throws JRException{
        JasperReport result=compiledReport;if(result!=null)return result;
        synchronized(this){if(compiledReport==null){InputStream in=getClass().getResourceAsStream("/reports/supplier-payment/supplier-payment.jrxml");if(in==null)throw new ReglaNegocioException("No se encontró la plantilla del comprobante de pago.");compiledReport=JasperCompileManager.compileReport(in);}return compiledReport;}
    }
    private static String value(String value){return value==null||value.isBlank()?"—":value.trim();}
    private static String optional(String value){return value==null||value.isBlank()?null:value.trim();}
    private static String money(java.math.BigDecimal amount,String currency){return SupplierCreditNotePdfService.currencyPrefix(currency)+" "+String.format(Locale.US,"%,.2f",amount);}
}
