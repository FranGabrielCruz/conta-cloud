package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.EstadoReciboCliente;
import com.citacloud.springboot.contacloud.app.reports.pos.InvoiceFormats;
import com.citacloud.springboot.contacloud.app.errors.TraceContext;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CobroClientePdfService {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final ReciboHistorialService historial;
    private final LocalFileStorageService storage;
    private final Map<String,JasperReport> templates=new HashMap<>();

    public CobroClientePdfService(ReciboHistorialService historial,LocalFileStorageService storage){
        this.historial=historial;this.storage=storage;
    }

    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('recibos.ver') and hasAuthority('recibos.imprimir')")
    public ArchivoPdfDto generar(UUID id){
        var detalle=historial.detalle(id);
        var pdf=new ArchivoPdfDto("Recibo_"+InvoiceFormats.fileName(detalle.recibo().numero()),render(detalle));
        LoggerFactory.getLogger(getClass()).atDebug().addKeyValue("ReceiptId",id)
            .addKeyValue("TraceId",TraceContext.currentOrCreate()).log("receipt_large_pdf_rendered");
        return pdf;
    }

    byte[] render(ReciboHistorialDto detalle){
        var r=detalle.recibo();var d=detalle.documento();var empresa=d.company();
        boolean voided=r.estado()==EstadoReciboCliente.VOIDED;
        String cliente=lines(d.customer().name(),optional("RNC/Cédula",d.customer().identification()));
        String datos=lines(optional("Sucursal",d.branch()),optional("Caja",d.cashRegister()),optional("Cajero",d.operator()),"Estado: "+(voided?"Anulado":"Emitido"));
        try{
            byte[] logo=empresa.logoObjectKey()==null?null:storage.load(empresa.logoObjectKey(),5L*1024*1024).orElse(null);
            int header=Math.max(96,78+Math.max(0,lineCount(empresa.name(),430,15)-1)*18
                +Math.max(0,lineCount(empresa.address(),430,9)-1)*11)+(voided?20:0);
            int box=28+Math.max(lineCount(cliente,365,9),lineCount(datos,370,9))*11;
            Map<String,Object> params=new HashMap<>();
            params.put("companyTradeName",clean(empresa.name()));
            params.put("companyTaxIdentification","DO".equals(empresa.countryCode())?PurchaseOrderPdfService.formatTaxIdentification(empresa.identification()):clean(empresa.identification()));
            params.put("companyAddress",clean(empresa.address()));
            params.put("companyPhone","DO".equals(empresa.countryCode())?PurchaseOrderPdfService.formatPhone(empresa.phone()):clean(empresa.phone()));
            params.put("companyLogo",logo==null?null:new ByteArrayInputStream(logo));
            params.put("orderNumber",d.receiptNumber());params.put("orderDate",DATE.format(d.receiptDateTime()));params.put("currencyCode",d.currency());
            params.put("supplierName",clean(d.customer().name()));params.put("clientInformation",cliente);params.put("affectedInformation",datos);
            params.put("emissionFooter",lines(optional("Emitido por",d.operator()),"Emisión: "+TIME.format(d.receiptDateTime())).replace('\n',' '));
            return JasperExportManager.exportReportToPdf(JasperFillManager.fillReport(template(logo!=null,header,box,voided),
                params,new JRMapCollectionDataSource(rows(detalle))));
        }catch(IOException|JRException|IllegalArgumentException|UncheckedIOException ex){
            throw new ReglaNegocioException("No fue posible generar el PDF del recibo. Puede volver a intentar la impresión.",ex);
        }
    }

    private synchronized JasperReport template(boolean logo,int header,int box,boolean voided)throws IOException,JRException{
        String key=logo+":"+header+":"+box+":"+voided;
        JasperReport report=templates.get(key);
        if(report==null){
            if(templates.size()>=32)templates.clear();
            report=JasperCompileManager.compileReport(new ByteArrayInputStream(LargeDocumentPdfTemplate.receipt(logo,header,box,voided).getBytes(StandardCharsets.UTF_8)));
            templates.put(key,report);
        }
        return report;
    }

    static List<Map<String,?>> rows(ReciboHistorialDto detalle){
        var d=detalle.documento();var r=detalle.recibo();
        List<Map<String,?>> rows=new ArrayList<>();
        rows.add(row("SECTION","DOCUMENTOS PAGADOS",null));
        if(d.documents().isEmpty())rows.add(row("TEXT","Sin documentos aplicados al emitir este recibo.",null));
        else{
            rows.add(row("DOC_HEADER",null,null));
            for(var doc:d.documents()){
                var legacy=r.aplicaciones().stream().filter(a->Objects.equals(a.factura(),doc.documentNumber())).findFirst().orElse(null);
                var fecha=doc.date()!=null?doc.date():legacy==null?null:legacy.fechaFactura();
                var original=doc.originalTotal()!=null?doc.originalTotal():legacy==null?null:legacy.totalDocumento();
                var fields=row("LINE",fecha==null?"—":DATE.format(fecha),null);
                fields.put("descripcion",clean(doc.documentNumber()));fields.put("precioUnitario",original);fields.put("total",doc.amount());
                rows.add(fields);
            }
        }
        rows.add(row("MONEY","Total aplicado",d.totalApplied()));
        rows.add(row("SECTION","FORMAS DE PAGO",null));
        for(var m:d.paymentMethods())if(m.amount().signum()>0)
            rows.add(row("FINANCE",clean(m.name())+(m.reference()==null?"":" · "+clean(m.reference())),m.amount()));
        rows.add(row("TOTAL","TOTAL COBRADO "+d.currency(),d.totalCollected()));
        var ref=com.citacloud.springboot.contacloud.app.repositories.PaymentReceiptSnapshotRepository.safeReference(r.referencia(),null);
        if(ref!=null)rows.add(row("TEXT",optional("Referencia",ref),null));
        if(r.observacion()!=null&&!r.observacion().isBlank())rows.add(row("TEXT",optional("Observación",r.observacion()),null));
        if(r.estado()==EstadoReciboCliente.VOIDED){
            rows.add(row("SECTION","ANULACIÓN",null));
            rows.add(row("TEXT",lines(optional("Motivo",r.motivoAnulacion()),optional("Anulado por",detalle.anuladoPor()),
                detalle.anuladoEn()==null?null:"Fecha: "+TIME.format(detalle.anuladoEn())),null));
        }
        return rows;
    }
    private static Map<String,Object> row(String kind,String label,BigDecimal amount){
        var fields=new HashMap<String,Object>();fields.put("rowKind",kind);fields.put("rowLabel",label);fields.put("rowAmount",amount);return fields;
    }
    private static String clean(String value){return value==null?null:value.replaceAll("[\\p{Cntrl}&&[^\\n]]"," ");}
    private static String optional(String label,String value){return value==null||value.isBlank()?null:label+": "+clean(value);}
    private static String lines(String... values){return Arrays.stream(values).filter(x->x!=null&&!x.isBlank()).map(CobroClientePdfService::clean).collect(Collectors.joining("\n"));}
    private static int lineCount(String text,int width,int size){
        if(text==null||text.isBlank())return 0;
        var font=new java.awt.Font("SansSerif",java.awt.Font.PLAIN,size);
        var ctx=new java.awt.font.FontRenderContext(null,true,true);
        return Arrays.stream(text.split("\n",-1)).mapToInt(p->Math.max(1,(int)Math.ceil(font.getStringBounds(p,ctx).getWidth()/width))).sum();
    }
}
