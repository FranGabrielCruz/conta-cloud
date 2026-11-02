package com.citacloud.springboot.contacloud.app.reports.pos;
import com.citacloud.springboot.contacloud.app.reports.pos.InvoiceFormats;
import com.citacloud.springboot.contacloud.app.reports.pos.PaymentReceiptPrintDocument;
import java.io.IOException;

/** Receipt composition on the shared physical 80 mm layout engine. */
public final class PaymentReceipt80mmPdfRenderer extends PosTicket80mmPdfRenderer<PaymentReceiptPrintDocument> {
    @Override protected void compose(Layout l,PaymentReceiptPrintDocument d) throws IOException {
        var c=d.company();l.center(c.name(),12,true);
        if(present(c.identification()))l.center(("DO".equalsIgnoreCase(c.countryCode())?"RNC: ":"ID: ")+InvoiceFormats.rnc(c.identification(),c.countryCode()),8.5f,false);
        if(present(c.address()))l.center(c.address(),8.5f,false);
        if(present(c.phone()))l.center("Tel.: "+InvoiceFormats.phone(c.phone(),c.countryCode()),8.5f,false);
        l.gap();l.center("RECIBO DE COBRO",10,true);l.rule();l.metadataColumn("RNC/Cédula");
        l.metadata("Recibo",d.receiptNumber());l.metadata("Fecha",InvoiceFormats.date(d.receiptDateTime()));l.metadata("Cliente",d.customer().name());
        if(present(d.customer().identification()))l.metadata("RNC/Cédula",d.customer().identification());
        if(present(d.branch()))l.metadata("Sucursal",d.branch());
        if(present(d.cashRegister()))l.metadata("Caja",d.cashRegister());
        if(present(d.operator()))l.metadata("Cajero",d.operator());l.rule();
        if(!d.documents().isEmpty()) {
            l.center("DOCUMENTOS PAGADOS",9,true);l.rule();l.columns("DOCUMENTO","ABONO",7.5f,true);l.rule();
            for(var row:d.documents())l.columns(row.documentNumber(),InvoiceFormats.amount(row.amount()),8.5f,false);
            l.rule();l.pair("Total aplicado",InvoiceFormats.amount(d.totalApplied()),8.5f,true);l.gap();
        } else if(present(d.concept())) {l.left("Concepto:",8.5f,true);l.left(d.concept(),8.5f,false);l.rule();}
        l.center("FORMA DE PAGO",9,true);l.rule();
        for(var m:d.paymentMethods()) {l.pair(m.name(),InvoiceFormats.amount(m.amount()),8.5f,false);if(present(m.reference()))l.left("Ref.: "+m.reference(),8,false);}
        l.rule();l.total("TOTAL COBRADO "+d.currency(),InvoiceFormats.amount(d.totalCollected()));l.rule();
        if(d.previousBalance()!=null||d.amountApplied()!=null||d.remainingBalance()!=null) {
            l.center("BALANCE CLIENTE",9,true);l.rule();
            if(d.previousBalance()!=null)l.pair("Balance anterior",InvoiceFormats.amount(d.previousBalance()),8.5f,false);
            if(d.amountApplied()!=null)l.pair("Monto aplicado",InvoiceFormats.amount(d.amountApplied()),8.5f,false);
            if(d.remainingBalance()!=null)l.pair("Balance pendiente",InvoiceFormats.amount(d.remainingBalance()),8.5f,false);l.rule();
        }
        l.gap();for(String s:d.footer())l.center(s,8.5f,false);
    }
}
