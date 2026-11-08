package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.reports.pos.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
final class PrintSnapshotFixtures {
 static final BigDecimal ONE=BigDecimal.ONE,ZERO=BigDecimal.ZERO;
 static final LocalDateTime DATE=LocalDateTime.of(2026,11,7,17,0);
 static PosInvoiceDocument invoice(){return new PosInvoiceDocument(new PosInvoiceDocument.Issuer("DEMO",null,null,null,"DO"),"FACTURA",null,"FAC-TEST",DATE,null,null,new PosInvoiceDocument.Customer("Cliente",null),List.of(new PosInvoiceDocument.Item(ONE,"Producto",ONE)),ONE,ZERO,List.of(),ONE,"DOP",List.of(),null,List.of(),false);}
 static SalesReturnPrintDocument salesReturn(){return new SalesReturnPrintDocument(new SalesReturnPrintDocument.CompanySnapshot("DEMO",null,null,null,"DO"),"DEV-TEST","FAC-TEST",DATE,new SalesReturnPrintDocument.CustomerSnapshot("Cliente",null),null,null,null,null,"DOP",List.of(new SalesReturnPrintDocument.ReturnedItem(ONE,"Producto",ONE)),ONE,null,null,List.of(),ONE,List.of(),null,List.of("Documento de devolución"),false);}
 static CashCountPrintDocument count(){return new CashCountPrintDocument(new CashCountPrintDocument.CompanySnapshot("DEMO",null,null,null,"DO"),"REC-TEST",DATE,null,DATE,null,"Caja",null,null,"DOP",List.of(),false,ONE,ONE,ZERO,CashCountPrintDocument.Status.CUADRADO,null,List.of());}
}
