package com.citacloud.springboot.contacloud.app.reports.pos;

import com.citacloud.springboot.contacloud.app.reports.pos.InvoiceFormats;
import com.citacloud.springboot.contacloud.app.reports.pos.CashVoucherPrintDocument;
import java.io.IOException;

/** Voucher composition on the common measured 80 mm layout; no financial calculations. */
public final class CashVoucher80mmPdfRenderer extends PosTicket80mmPdfRenderer<CashVoucherPrintDocument> {
    @Override protected void compose(Layout l,CashVoucherPrintDocument d) throws IOException {
        var c=d.company();l.center(c.name(),12,true);
        if(present(c.identification()))l.center(("DO".equalsIgnoreCase(c.countryCode())?"RNC: ":"ID: ")+InvoiceFormats.rnc(c.identification(),c.countryCode()),8.5f,false);
        if(present(c.address()))l.center(c.address(),8.5f,false);
        if(present(c.phone()))l.center("Tel.: "+InvoiceFormats.phone(c.phone(),c.countryCode()),8.5f,false);
        l.gap();l.center("VALE DE CAJA",10,true);l.rule();l.metadataColumn("Sucursal");
        l.metadata("Vale",d.voucherNumber());l.metadata("Fecha",InvoiceFormats.date(d.createdDateTime()));l.metadata("Caja",d.cashRegister());
        if(present(d.branch()))l.metadata("Sucursal",d.branch());
        if(present(d.cashier()))l.metadata("Cajero",d.cashier());l.rule();
        l.center(d.movementType()==CashVoucherPrintDocument.MovementType.CASH_IN?"ENTRADA DE CAJA":"SALIDA DE CAJA",11,true);l.rule();
        if(present(d.cancellationReason())){l.center("ANULADO",11,true);section(l,"Motivo de anulación:",d.cancellationReason());l.rule();}
        section(l,"Concepto:",d.concept());l.rule();
        l.pair("Monto",InvoiceFormats.amount(d.amount()),9,false);l.rule();
        l.total("TOTAL "+d.currency(),InvoiceFormats.amount(d.amount()));l.rule();
        if(present(d.paymentMethod())){l.metadataColumn("Forma de pago");l.metadata("Forma de pago",d.paymentMethod());}
        if(present(d.reference()))section(l,"Referencia:",d.reference());
        if(present(d.observation()))section(l,"Observación:",d.observation());
        if(present(d.performedBy()))section(l,"Realizado por:",d.performedBy());
        if(d.relatedParty()!=null)section(l,switch(d.relatedParty().label()){case BENEFICIARIO->"Beneficiario:";case ENTREGADO_A->"Entregado a:";case RECIBIDO_POR->"Recibido por:";},d.relatedParty().name());
        if(d.showSignature()){l.rule();l.gap();l.center("__________________",9,false);l.center("Firma",8.5f,false);l.gap();}
        l.gap();for(String line:d.footer())l.center(line,8.5f,false);
    }
    private static void section(Layout l,String title,String text) throws IOException {l.left(title,8.5f,true);l.left(text,8.5f,false);l.gap();}
}
