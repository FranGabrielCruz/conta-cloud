package com.citacloud.springboot.contacloud.app.reports.pos;

import com.citacloud.springboot.contacloud.app.reports.pos.InvoiceFormats;
import com.citacloud.springboot.contacloud.app.reports.pos.CashCountPrintDocument;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;

/** Cash-count composition only; consumes the confirmed financial and historical values. */
public final class CashCount80mmPdfRenderer extends PosTicket80mmPdfRenderer<CashCountPrintDocument> {
    @Override protected void compose(Layout l,CashCountPrintDocument d) throws IOException {
        var c=d.company();l.center(c.name(),12,true);
        if(present(c.identification()))l.center(("DO".equalsIgnoreCase(c.countryCode())?"RNC: ":"ID: ")+InvoiceFormats.rnc(c.identification(),c.countryCode()),8.5f,false);
        if(present(c.address()))l.center(c.address(),8.5f,false);
        if(present(c.phone()))l.center("Tel.: "+InvoiceFormats.phone(c.phone(),c.countryCode()),8.5f,false);
        l.gap();l.center("RECUENTO DE CAJA",10,true);l.rule();l.metadataColumn("Recuento");
        l.metadata("Recuento",d.cashCountNumber());l.metadata("Fecha",InvoiceFormats.date(d.createdDateTime()));l.metadata("Caja",d.cashRegister());
        if(present(d.branch()))l.metadata("Sucursal",d.branch());
        if(present(d.cashier()))l.metadata("Cajero",d.cashier());l.rule();
        if(d.openingDateTime()!=null)l.metadata("Apertura",InvoiceFormats.date(d.openingDateTime()));
        l.metadata("Recuento",InvoiceFormats.date(d.countDateTime()));l.rule();
        l.center("EFECTIVO CONTADO",9,true);l.rule();
        Comparator<CashCountPrintDocument.Denomination> order=d.denominations().stream().anyMatch(x->x.displayOrder()!=null)
            ?Comparator.comparing(CashCountPrintDocument.Denomination::displayOrder,Comparator.nullsLast(Comparator.naturalOrder()))
            :Comparator.comparing(CashCountPrintDocument.Denomination::denomination).reversed();
        var rows=d.denominations().stream().filter(x->d.includeZeroDenominations()||x.quantity()!=0).sorted(order)
            .map(x->List.of(InvoiceFormats.amount(x.denomination()),Integer.toString(x.quantity()),InvoiceFormats.amount(x.amount()))).toList();
        l.numericTable(List.of("DENOM.","CANT.","IMPORTE"),rows);l.rule();
        l.total("TOTAL EFECTIVO "+d.currency(),InvoiceFormats.amount(d.countedCash()));l.rule();
        l.center("RESUMEN DE CAJA",9,true);l.rule();
        l.pair("Saldo esperado",InvoiceFormats.amount(d.expectedCash()),8.5f,false);
        l.pair("Efectivo contado",InvoiceFormats.amount(d.countedCash()),8.5f,false);l.rule();
        l.total("DIFERENCIA",InvoiceFormats.amount(d.difference()));l.rule();
        l.pair("Estado",d.status().name(),9,true);l.rule();
        if(present(d.observation())){l.left("Observación:",8.5f,true);l.left(d.observation(),8.5f,false);l.gap();}
        if(present(d.performedBy())){l.left("Realizado por:",8.5f,true);l.left(d.performedBy(),8.5f,false);l.gap();}
        l.gap();for(String line:d.footer())l.center(line,8.5f,false);
    }
}
