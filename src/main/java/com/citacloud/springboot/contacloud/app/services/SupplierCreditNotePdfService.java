package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.SupplierCreditNoteReportData;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class SupplierCreditNotePdfService {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private volatile JasperReport compiledReport;

    public byte[] generate(SupplierCreditNoteReportData data){
        try{
            JasperPrint print=JasperFillManager.fillReport(report(),parameters(data),
                new JRBeanCollectionDataSource(data.lines()));
            return JasperExportManager.exportReportToPdf(print);
        }catch(JRException ex){throw new ReglaNegocioException("No fue posible generar el PDF de la nota de crédito.",ex);}
    }

    static Map<String,Object> parameters(SupplierCreditNoteReportData data){
        Map<String,Object> p=new HashMap<>();
        p.put("companyTradeName",value(data.companyTradeName()));p.put("companyLegalName",optional(data.companyLegalName()));
        p.put("companyTaxIdentification",PurchaseOrderPdfService.formatTaxIdentification(data.companyTaxIdentification()));
        p.put("companyAddress",optional(data.companyAddress()));p.put("companyPhone",PurchaseOrderPdfService.formatPhone(data.companyPhone()));
        p.put("companyEmail",optional(data.companyEmail()));p.put("companyLogo",data.companyLogo()==null?null:new ByteArrayInputStream(data.companyLogo()));
        p.put("noteNumber",value(data.noteNumber()));p.put("noteDate",format(data.noteDate()));p.put("status",value(data.status()));
        p.put("supplierName",value(data.supplierName()));p.put("supplierTaxIdentification",value(PurchaseOrderPdfService.formatTaxIdentification(data.supplierTaxIdentification())));
        p.put("supplierCreditNumber",value(data.supplierCreditNumber()));p.put("fiscalNumber",value(data.fiscalNumber()));
        p.put("currencyCode",value(data.currencyCode()));p.put("currencyPrefix",currencyPrefix(data.currencyCode()));
        p.put("reason",value(data.reason()));p.put("relatedInvoice",value(data.relatedInvoice()));p.put("notes",optional(data.notes()));
        p.put("subtotal",data.subtotal());p.put("discount",data.discount());p.put("tax",data.tax());p.put("total",data.total());
        p.put("applied",data.applied());p.put("available",data.available());
        p.put("generatedByDisplayName",value(data.generatedByDisplayName()));
        p.put("generatedAt",data.generatedAt()==null?"":DATE_TIME.format(data.generatedAt()));
        p.put("taxDataSource",new JRBeanCollectionDataSource(data.taxes()));
        p.put("applicationDataSource",new JRBeanCollectionDataSource(data.applications()));
        p.put("hasTaxes",!data.taxes().isEmpty());p.put("hasApplications",!data.applications().isEmpty());
        return p;
    }

    private JasperReport report() throws JRException{
        JasperReport result=compiledReport;if(result!=null)return result;
        synchronized(this){
            if(compiledReport==null){
                InputStream in=getClass().getResourceAsStream("/reports/supplier-credit-note/supplier-credit-note.jrxml");
                if(in==null)throw new ReglaNegocioException("No se encontró la plantilla de la nota de crédito.");
                compiledReport=JasperCompileManager.compileReport(in);
            }
            return compiledReport;
        }
    }

    private static String format(java.time.LocalDate date){return date==null?"—":DATE.format(date);}
    private static String value(String value){return value==null||value.isBlank()?"—":value.trim();}
    private static String optional(String value){return value==null||value.isBlank()?null:value.trim();}
    static String currencyPrefix(String code){return "DOP".equalsIgnoreCase(code)?"RD$":value(code).toUpperCase(Locale.ROOT);}
}
