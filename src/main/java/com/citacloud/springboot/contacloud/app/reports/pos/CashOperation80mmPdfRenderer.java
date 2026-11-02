package com.citacloud.springboot.contacloud.app.reports.pos;
import java.io.IOException;
public class CashOperation80mmPdfRenderer extends PosTicket80mmPdfRenderer<CashOperationPrintDocument>{
 @Override protected void compose(Layout p,CashOperationPrintDocument d)throws IOException{
  p.center(d.company(),11,true);if(present(d.taxId()))p.center("RNC: "+d.taxId(),8,false);p.rule();p.center(d.title(),11,true);p.metadataColumn("Documento");
  p.metadata("Documento",d.number());if(present(d.date()))p.metadata("Fecha",d.date());if(present(d.branch()))p.metadata("Sucursal",d.branch());if(present(d.cash()))p.metadata("Caja",d.cash());if(present(d.user()))p.metadata("Usuario",d.user());p.rule();
  for(var row:d.lines())p.pair(row.label(),row.value()==null?"—":row.value(),8.5f,false);
  p.rule();p.total(d.totalLabel(),d.total());for(var note:d.notes())if(present(note))p.left(note,8,false);p.rule();p.center("Conserve este comprobante",8,false);
 }
}
