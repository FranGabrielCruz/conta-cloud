// Rendering core mirrored from contaCloudPrint; see docs/DEVOLUCION-POS-80MM.md.
package com.citacloud.springboot.contacloud.app.reports.pos;

import com.citacloud.springboot.contacloud.app.reports.pos.InvoiceFormats;
import com.citacloud.springboot.contacloud.app.reports.pos.SalesReturnPrintDocument;
import java.io.IOException;

/** Specific sales-return composition, independent of the invoice document. */
public final class SalesReturn80mmPdfRenderer extends PosTicket80mmPdfRenderer<SalesReturnPrintDocument> {
    @Override protected void compose(Layout layout, SalesReturnPrintDocument document) throws IOException {
        var company=document.company();
        layout.center(company.name(),12,true);
        if(present(company.identification()))layout.center(("DO".equalsIgnoreCase(company.countryCode())?"RNC: ":"ID: ")+InvoiceFormats.rnc(company.identification(),company.countryCode()),8.5f,false);
        if(present(company.address()))layout.center(company.address(),8.5f,false);
        if(present(company.phone()))layout.center("Tel.: "+InvoiceFormats.phone(company.phone(),company.countryCode()),8.5f,false);
        layout.gap();if(document.testDocument())layout.center("DOCUMENTO DE PRUEBA",10,true);
        layout.center("DEVOLUCIÓN DE VENTA",10,true);layout.rule();layout.metadataColumn("Factura origen");
        layout.metadata("Devolución",document.returnNumber());layout.metadata("Factura origen",document.originalInvoiceNumber());
        layout.metadata("Fecha",InvoiceFormats.date(document.returnDateTime()));layout.metadata("Cliente",document.customer().name());
        if(present(document.customer().identification()))layout.metadata("RNC/Cédula",document.customer().identification());
        if(present(document.branch()))layout.metadata("Sucursal",document.branch());
        if(present(document.cashRegister()))layout.metadata("Caja",document.cashRegister());
        if(present(document.operator()))layout.metadata("Cajero",document.operator());
        if(present(document.reason()))layout.metadata("Motivo",document.reason());
        layout.rule();float amountWidth=58;
        for(var item:document.items())amountWidth=Math.max(amountWidth,layout.measure(InvoiceFormats.amount(item.lineTotal()),8.5f));
        layout.item("CANT.","DESCRIPCIÓN","IMPORTE",amountWidth,true);layout.rule();
        for(var item:document.items())layout.item(InvoiceFormats.quantity(item.returnedQuantity()),item.description(),InvoiceFormats.amount(item.lineTotal()),amountWidth,false);
        layout.rule();layout.pair("Subtotal devuelto",InvoiceFormats.amount(document.subtotalReturned()),8.5f,false);
        if(document.discountReturned()!=null&&document.discountReturned().signum()!=0)layout.pair("Descuento",InvoiceFormats.amount(document.discountReturned()),8.5f,false);
        if(document.surchargeReturned()!=null&&document.surchargeReturned().signum()!=0)layout.pair("Recargo",InvoiceFormats.amount(document.surchargeReturned()),8.5f,false);
        for(var tax:document.taxesReturned())layout.pair(tax.label().equals("Impuestos")?tax.label():"Impuestos ("+tax.label()+")",InvoiceFormats.amount(tax.amount()),8.5f,false);
        layout.rule();layout.total("TOTAL DEVUELTO "+document.currency(),InvoiceFormats.amount(document.totalReturned()));layout.rule();
        if(!document.refundMethods().isEmpty()) {
            layout.left("FORMA DE REEMBOLSO",8.5f,true);
            for(var refund:document.refundMethods())layout.pair(refund.label(),InvoiceFormats.amount(refund.amount()),8.5f,false);
            if(document.totalRefunded()!=null)layout.pair("TOTAL REEMBOLSADO",InvoiceFormats.amount(document.totalRefunded()),8.5f,true);
            layout.rule();
        }
        layout.gap();for(String footer:document.footer())layout.center(footer,8.5f,false);
    }
}
