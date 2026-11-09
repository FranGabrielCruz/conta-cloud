package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.EstadoReciboCliente;
import com.citacloud.springboot.contacloud.app.reports.pos.PaymentReceiptPrintDocument;
import com.citacloud.springboot.contacloud.app.reports.pos.PaymentReceiptPrintDocument.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReciboGrandePdfTest {
    private static BigDecimal monto(String valor){return new BigDecimal(valor);}
    @Test void usaImportesConfirmadosYDistingueOriginalDeAplicado() throws Exception {
        var pdf=new CobroClientePdfService(mock(ReciboHistorialService.class),mock(LocalFileStorageService.class));
        for(boolean anulado:List.of(false,true)){
            var fecha=LocalDateTime.of(2026,11,8,18,30);
            var d=new PaymentReceiptPrintDocument(new CompanySnapshot("Empresa de prueba","124543678","Av. Principal No. 25","2345678665","DO"),
                "REC-PRUEBA-25",fecha,new CustomerSnapshot("Consumidor Final",null),"Sucursal Principal","Caja 01","cajero",null,"USD",
                List.of(new PaidDocument("FAC-000016",monto("300"),fecha.toLocalDate(),monto("1000"),"INVOICE",UUID.randomUUID()),
                    new PaidDocument("NDV-000010",monto("200"),fecha.toLocalDate(),monto("200"),"DEBIT_NOTE",UUID.randomUUID())),
                monto("500"),List.of(new PaymentMethod("Efectivo",monto("300"),null),new PaymentMethod("Tarjeta",monto("200"),"**** 4821")),
                monto("500"),null,null,null,List.of());
            var r=new CobroClienteDto(UUID.randomUUID(),d.receiptNumber(),null,"Consumidor Final",null,UUID.randomUUID(),"USD",
                UUID.randomUUID(),d.branch(),fecha.toLocalDate(),fecha.atOffset(ZoneOffset.ofHours(-4)),monto("500"),monto("500"),
                BigDecimal.ZERO,anulado?EstadoReciboCliente.VOIDED:EstadoReciboCliente.APPLIED,null,"Pago de prueba.",d.operator(),
                anulado?"Duplicado registrado por error":null,List.of(),List.of());
            byte[] bytes=pdf.render(new ReciboHistorialDto(r,d,anulado?fecha.plusMinutes(45):null,anulado?"admin":null,!anulado));
            try(var doc=Loader.loadPDF(bytes)){
                String text=new PDFTextStripper().getText(doc);
                assertThat(doc.getNumberOfPages()).isEqualTo(1);
                var pagina=doc.getPage(0);
                assertThat(pagina.getRotation()%180==90?pagina.getMediaBox().getHeight():pagina.getMediaBox().getWidth()).isEqualTo(842f);
                assertThat(text).contains("RECIBO DE COBRO","REC-PRUEBA-25","Consumidor Final","FAC-000016","NDV-000010","TOTAL DOCUMENTO",
                    "1,000.00","300.00","TOTAL COBRADO USD","500.00","Efectivo","Tarjeta","4821","Caja 01");
                assertThat(text).doesNotContain("ContaCloud","RNC/Cédula:","Referencia: —");
                if(anulado)assertThat(text).contains("ANULADO","Duplicado registrado por error","Anulado por: admin");
                else assertThat(text).doesNotContain("ANULADO","ANULACIÓN");
                NotaCreditoVentaPdfServiceTest.assertBounds(doc);
            }
            java.nio.file.Files.write(java.nio.file.Path.of("target","recibo-grande-"+(anulado?"anulado":"emitido")+".pdf"),bytes);
        }
    }
}
