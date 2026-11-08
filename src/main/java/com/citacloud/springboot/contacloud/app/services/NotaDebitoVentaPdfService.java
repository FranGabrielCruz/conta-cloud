package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.repositories.NotaDebitoVentaRepository;
import com.citacloud.springboot.contacloud.app.reports.pos.InvoiceFormats;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import java.io.*;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Uses the official OC Jasper base; never sends print jobs or financial commands. */
@Service
public class NotaDebitoVentaPdfService {
    private final NotaDebitoVentaRepository documentos;
    private final Map<String,JasperReport> templates=new HashMap<>();
    public NotaDebitoVentaPdfService(NotaDebitoVentaRepository documentos){this.documentos=documentos;}
    @Transactional(readOnly=true) @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('notas_debito.pdf')")
    public ArchivoPdfDto generar(UUID id){var d=documentos.documento(id);return new ArchivoPdfDto("NotaDebito_"+InvoiceFormats.fileName(d.numero()),render(d,documentos.ciclo(id,false)));}
    public byte[] render(NotaDebitoVentaDocumento d,NotaDebitoVentaCicloDto ciclo){
        try{
            String client=lines(NotaCreditoVentaService.nombreCliente(d.cliente()),optional("RNC/Cédula",d.identificacion()),null);
            String affected=lines("Factura: "+d.factura(),"Fecha: "+date(d.fechaFactura()),optional("Condición factura",d.condicionPago()));
            boolean voided="VOIDED".equals(ciclo.estado());
            String note=lines(voided?"ANULADA - documento sin vigencia. Los importes corresponden a su emisión original.":null,optional("Motivo",d.motivo()),d.descripcion(),"Condición de la nota: "+(d.condicionNota()==com.citacloud.springboot.contacloud.app.models.TipoCondicionPago.CASH?"Contado":"Crédito"));
            boolean logo=d.logoEmpresa()!=null&&d.logoEmpresa().length>0;
            int boxHeight=28+Math.max(lineCount(client,365,9),lineCount(affected,370,9))*11;
            int noteHeight=20+lineCount(note,782,9)*11;
            int headerHeight=Math.max(96,78+Math.max(0,lineCount(d.empresa(),logo?430:520,15)-1)*18+Math.max(0,lineCount(d.empresaLegal(),430,9)-1)*11+Math.max(0,lineCount(d.direccionEmpresa(),430,9)-1)*11);
            if(voided)headerHeight+=20;
            String key=voided+":"+logo+":"+headerHeight+":"+boxHeight+":"+noteHeight;
            JasperReport report;
            synchronized(templates){report=templates.get(key);if(report==null){if(templates.size()>=64)templates.clear();report=JasperCompileManager.compileReport(new ByteArrayInputStream(LargeDocumentPdfTemplate.debitNote(logo,headerHeight,boxHeight,noteHeight,voided).getBytes(java.nio.charset.StandardCharsets.UTF_8)));templates.put(key,report);}}
            var parameters=parameters(d);parameters.put("clientInformation",client);parameters.put("affectedInformation",affected);parameters.put("noteInformation",note);
            JasperPrint print=JasperFillManager.fillReport(report,parameters,new JRMapCollectionDataSource(rows(d)));
            return JasperExportManager.exportReportToPdf(print);
        }catch(IOException|JRException|IllegalArgumentException ex){throw new ReglaNegocioException("La nota permanece emitida. No fue posible generar su PDF; puede reintentar la impresión.",ex);}
    }
    static Map<String,Object> parameters(NotaDebitoVentaDocumento d){
        Map<String,Object> p=new HashMap<>();p.put("companyTradeName",clean(d.empresa()));p.put("companyLegalName",Objects.equals(d.empresa(),d.empresaLegal())?null:clean(d.empresaLegal()));
        p.put("companyTaxIdentification",PurchaseOrderPdfService.formatTaxIdentification(d.rnc()));p.put("companyAddress",clean(d.direccionEmpresa()));p.put("companyPhone",PurchaseOrderPdfService.formatPhone(d.telefono()));p.put("companyEmail",clean(d.correoEmpresa()));p.put("companyLogo",d.logoEmpresa()==null?null:new ByteArrayInputStream(d.logoEmpresa()));
        p.put("orderNumber",d.numero());p.put("orderDate",date(d.fecha()));p.put("currencyCode",d.moneda());p.put("supplierName",NotaCreditoVentaService.nombreCliente(d.cliente()));
        String issued=d.emitidaEn()==null?null:d.emitidaEn().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));p.put("emissionFooter",linesInline(optional("Emitida por",d.emitidaPor()),issued));return p;
    }
    static List<Map<String,?>> rows(NotaDebitoVentaDocumento d){
        List<Map<String,?>> result=new ArrayList<>();
        for(var l:d.lineas()){var row=new HashMap<String,Object>();row.put("rowKind","LINE");row.put("cantidad",l.cantidad());row.put("descripcion",clean(l.descripcion()));row.put("precioUnitario",l.precio());row.put("descuento",l.descuento());row.put("impuesto",l.impuesto());row.put("total",l.total());result.add(row);}
        result.add(row("MONEY","Subtotal",d.subtotal()));result.add(row("MONEY","Descuento",d.descuento()));
        var taxes=new LinkedHashMap<String,BigDecimal>();for(var l:d.lineas())if(l.impuesto().signum()!=0)taxes.merge(taxLabel(l.impuestoNombre(),l.tasa()),l.impuesto(),BigDecimal::add);taxes.forEach((label,amount)->result.add(row("MONEY",label,amount)));
        result.add(row("TOTAL","TOTAL "+d.moneda(),d.total()));
        if(d.condicionNota()==com.citacloud.springboot.contacloud.app.models.TipoCondicionPago.CASH){
            result.add(row("SECTION","FORMA DE PAGO",null));for(var p:d.pagos())result.add(row("FINANCE",p.metodo(),p.monto()));
            result.add(row("FINANCE","TOTAL PAGADO",d.pagado()));result.add(row("FINANCE","Recibido",d.recibido()));if(d.cambio().signum()>0)result.add(row("FINANCE","Cambio",d.cambio()));result.add(row("FINANCE","Saldo de la nota",d.saldoNota()));
        }else{
        result.add(row("SECTION","EFECTO FINANCIERO EN LA EMISIÓN",null));
        result.add(row("FINANCE","Saldo anterior",d.saldoAnterior()));
        result.add(row("FINANCE","Cargo de la nota",d.total()));
        result.add(row("FINANCE","Saldo posterior",d.saldoPosterior()));
        result.add(row("FINANCE","Saldo inicial de la nota",d.saldoNota()));
        }
        return result;
    }
    private static Map<String,?> row(String kind,String label,BigDecimal amount){var r=new HashMap<String,Object>();r.put("rowKind",kind);r.put("rowLabel",label);r.put("rowAmount",amount);return r;}
    private static String date(java.time.LocalDate date){return date==null?"-":date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));}
    private static String clean(String value){return value==null?null:value.replace('—','-').replace('–','-').replaceAll("[\\p{Cntrl}&&[^\\n]]"," ");}
    private static String optional(String label,String value){return value==null||value.isBlank()?null:label+": "+clean(value);}
    private static String lines(String... values){return Arrays.stream(values).filter(x->x!=null&&!x.isBlank()).map(NotaDebitoVentaPdfService::clean).collect(java.util.stream.Collectors.joining("\n"));}
    private static String linesInline(String... values){return Arrays.stream(values).filter(x->x!=null&&!x.isBlank()).collect(java.util.stream.Collectors.joining(" · "));}
    private static int lineCount(String text,int width,int size){
        if(text==null||text.isBlank())return 0;var font=new java.awt.Font("SansSerif",java.awt.Font.PLAIN,size);var context=new java.awt.font.FontRenderContext(null,true,true);int count=0;
        for(String paragraph:text.split("\\n",-1)){double measured=font.getStringBounds(paragraph,context).getWidth();count+=Math.max(1,(int)Math.ceil(measured/width));}return count;
    }
    static String taxLabel(String name,BigDecimal rate){
        String label=name==null||name.isBlank()?"Impuesto":name.trim();
        if(rate==null)return label.toUpperCase(Locale.ROOT);
        var trailing=java.util.regex.Pattern.compile("\\s+(\\d+(?:[.,]\\d+)?)\\s*%?$").matcher(label);
        if(trailing.find()&&new BigDecimal(trailing.group(1).replace(',','.')).compareTo(rate)==0)label=label.substring(0,trailing.start()).trim();
        return label.toUpperCase(Locale.ROOT)+" "+rate.stripTrailingZeros().toPlainString()+"%";
    }
}
