package com.citacloud.springboot.contacloud.app.reports.pos;

import com.citacloud.springboot.contacloud.app.reports.pos.InvoiceFormats;
import com.citacloud.springboot.contacloud.app.reports.pos.CashClosingPrintDocument;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;

/** Session report with full counted denominations. No business formula is executed here. */
public final class CashClosing80mmPdfRenderer extends PosTicket80mmPdfRenderer<CashClosingPrintDocument> {
    @Override protected void compose(Layout l,CashClosingPrintDocument d) throws IOException {
        var c=d.company();l.center(c.name(),12,true);
        if(present(c.identification()))l.center(("DO".equalsIgnoreCase(c.countryCode())?"RNC: ":"ID: ")+InvoiceFormats.rnc(c.identification(),c.countryCode()),8.5f,false);
        if(present(c.address()))l.center(c.address(),8.5f,false);
        if(present(c.phone()))l.center("Tel.: "+InvoiceFormats.phone(c.phone(),c.countryCode()),8.5f,false);
        l.gap();l.center("CIERRE DE CAJA",10,true);l.rule();l.metadataColumn("Documento");
        l.metadata("Documento",d.closingNumber());l.metadata("Fecha",InvoiceFormats.date(d.closingDateTime()));
        if(present(d.branch()))l.metadata("Sucursal",d.branch());
        l.metadata("Caja",d.cashRegister());if(present(d.cashier()))l.metadata("Usuario",d.cashier());l.rule();
        heading(l,"DATOS DEL TURNO");if(present(d.shiftNumber()))l.metadata("Turno",d.shiftNumber());
        l.metadata("Apertura",InvoiceFormats.date(d.openingDateTime()));l.rule();
        heading(l,"RESUMEN DE EFECTIVO");
        if(d.cashSummary()!=null){l.pair("Fondo inicial",InvoiceFormats.amount(d.cashSummary().openingFund()),8.5f,false);l.pair("Entradas efectivo",InvoiceFormats.amount(d.cashSummary().cashEntries()),8.5f,false);l.pair("Salidas efectivo",InvoiceFormats.amount(d.cashSummary().cashExits()),8.5f,false);l.rule();}
        l.total("EFECTIVO ESPERADO",InvoiceFormats.amount(d.expectedCash()));l.rule();
        heading(l,"FORMAS DE COBRO");
        var methods=d.collectionMethods()==null?d.paymentMethods():d.collectionMethods();
        var payments=methods.stream().filter(x->x.amount().signum()!=0).sorted(Comparator.comparing(CashClosingPrintDocument.PaymentMethod::displayOrder,Comparator.nullsLast(Comparator.naturalOrder()))).toList();
        if(d.collectionMethods()==null&&present(d.paymentDescription()))l.left("Resumen histórico: "+d.paymentDescription(),8,false);
        for(var payment:payments)l.pair(payment.name(),InvoiceFormats.amount(payment.amount()),8.5f,false);
        if(d.totalCollected()!=null){l.rule();l.total("TOTAL COBRADO",InvoiceFormats.amount(d.totalCollected()));}
        else if(d.paymentTotal()!=null){l.rule();l.total(d.paymentTotal().label(),InvoiceFormats.amount(d.paymentTotal().amount()));}
        l.rule();heading(l,"CUADRE DE CAJA");l.pair("Efectivo esperado",InvoiceFormats.amount(d.expectedCash()),8.5f,false);l.pair("Efectivo contado",InvoiceFormats.amount(d.countedCash()),8.5f,false);l.rule();
        l.total("DIFERENCIA "+d.currency(),InvoiceFormats.amount(d.difference()));l.pair("Estado",d.status().name(),9,true);l.rule();
        heading(l,"DESGLOSE DEL EFECTIVO CONTADO");
        Comparator<CashClosingPrintDocument.Denomination> order=d.denominations().stream().anyMatch(x->x.displayOrder()!=null)
            ?Comparator.comparing(CashClosingPrintDocument.Denomination::displayOrder,Comparator.nullsLast(Comparator.naturalOrder()))
            :Comparator.comparing(CashClosingPrintDocument.Denomination::denomination).reversed();
        var rows=d.denominations().stream().filter(x->x.quantity()>0).sorted(order)
            .map(x->List.of(InvoiceFormats.amount(x.denomination()),Integer.toString(x.quantity()),InvoiceFormats.amount(x.amount()))).toList();
        l.numericTable(List.of("DENOMINACIÓN","CANT.","IMPORTE"),rows);l.rule();l.total("TOTAL CONTADO",InvoiceFormats.amount(d.countedCash()));l.rule();
        if(present(d.observation())){heading(l,"OBSERVACIÓN");l.left(d.observation(),8.5f,false);l.gap();}
        if(present(d.closedBy()))section(l,"Cerrado por:",d.closedBy());
        if(d.signatureRequired()){l.rule();l.gap();l.center("__________________",9,false);l.center("Firma",8.5f,false);l.gap();}
        l.gap();for(String line:d.footer())l.center(line,8.5f,false);
    }
    private static void heading(Layout l,String title) throws IOException {l.center(title,9,true);l.rule();}
    private static void section(Layout l,String title,String text) throws IOException {l.left(title,8.5f,true);l.left(text,8.5f,false);l.gap();}
}
