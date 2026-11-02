package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.reports.pos.SalesReturnPrintDocument;
import com.citacloud.springboot.contacloud.app.reports.pos.SalesReturnPrintDocument.*;
import com.citacloud.springboot.contacloud.app.reports.pos.SalesReturn80mmPdfRenderer;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SalesReturnPdfTest {
    private final SalesReturn80mmPdfRenderer renderer=new SalesReturn80mmPdfRenderer();
    private static BigDecimal money(String value){return new BigDecimal(value);}
    private SalesReturnPrintDocument fixture(int count,String scenario) {
        String company=scenario.equals("empresa-larga")?"Comercializadora de artículos y servicios profesionales con nombre comercial muy largo, SRL":"EMPRESA DE DEMOSTRACIÓN";
        String customer=scenario.equals("cliente-largo")?"José Pérez y asociados, cliente con nombre completo muy largo para comprobar el ajuste de texto":"Consumidor Final";
        String description=scenario.equals("descripcion-larga")?"Hamburguesa especial con queso y tocineta, Café y Piña, Reparación e Información completas":"naranja";
        String reason=scenario.equals("motivo-largo")?"Producto recibido con daños en el empaque. Se conserva el motivo completo sin recortar ni consultar el catálogo actual.":"Producto defectuoso";
        String total=scenario.equals("importe-grande")?"9999999.99":"14.16";
        List<ReturnedItem> items=new ArrayList<>();for(int i=0;i<count;i++)items.add(new ReturnedItem(money(i%2==0?"1.000000":"1.500000"),description,money(total),money("12"),BigDecimal.ZERO,money("2.16")));
        var refunds=scenario.equals("multipago")?List.of(new AmountLine("Efectivo",money("5")),new AmountLine("Tarjeta",money("9.16"))):List.of(new AmountLine("Saldo a favor",money(total)));
        return new SalesReturnPrintDocument(new CompanySnapshot(company,"124543678","Av. Principal No. 25\nSanto Domingo, R.D.","8095551234","DO"),
                "DEV-PRUEBA-09","FAC-PRUEBA-17",LocalDateTime.of(2026,11,1,15,8),new CustomerSnapshot(customer,null),null,"Caja 02","José Pérez",reason,
                "DOP",items,money("12"),null,null,List.of(new AmountLine("ITBIS",money("2.16"))),money(total),refunds,scenario.equals("multipago")?money("14.16"):null,
                List.of("Documento de devolución","Conserve este comprobante"),false);
    }
    @ParameterizedTest @ValueSource(ints={1,5,20,30})
    void returnedItemsAreCompactAndHeightGrowsWithoutChangingSourceAmounts(int count) throws Exception {
        try(var pdf=Loader.loadPDF(renderer.render(fixture(count,"normal")))) {
            String text=new PDFTextStripper().getText(pdf);
            for(String expected:List.of("DEVOLUCIÓN DE VENTA","Devolución:","Factura origen:","DEV-PRUEBA-09","FAC-PRUEBA-17","01/11/2026 15:08","RNC: 124-54367-8","809-555-1234","CANT.","DESCRIPCIÓN","IMPORTE","Subtotal devuelto:","Impuestos (ITBIS):","TOTAL DEVUELTO DOP:","14.16","FORMA DE REEMBOLSO","Saldo a favor:","Documento de devolución"))assertTrue(text.contains(expected),expected);
            assertFalse(text.contains("ContaCloud"));assertFalse(text.contains("Gracias por su compra"));assertFalse(text.contains("1.000000"));
            assertTrue(pdf.getPage(0).getMediaBox().getHeight()<400+count*12);
            assertEquals(count,text.split("naranja",-1).length-1);checkGeometry(pdf);
        }
    }
    @Test void optionalFieldsForeignIdentifiersCurrencyAndMultipleTaxesRemainDynamic() throws Exception {
        var source=fixture(1,"normal");
        var document=new SalesReturnPrintDocument(new CompanySnapshot("Empresa", "FOREIGN-ID-123",null,"+34 911234567","ES"),source.returnNumber(),source.originalInvoiceNumber(),source.returnDateTime(),
                new CustomerSnapshot("Cliente identificado","001-0000000-0"),null,null,null,null,"EUR",source.items(),money("12"),money("1"),money("2"),
                List.of(new AmountLine("IVA",money("2.16")),new AmountLine("Otro",money("0.50"))),money("999.99"),List.of(new AmountLine("Tarjeta",money("999.99"))),null,List.of("Pie propio"),false);
        try(var pdf=Loader.loadPDF(renderer.render(document))) {
            String text=new PDFTextStripper().getText(pdf);
            for(String expected:List.of("ID: FOREIGN-ID-123","+34 911234567","001-0000000-0","Descuento:","Recargo:","Impuestos (IVA):","Impuestos (Otro):","TOTAL DEVUELTO EUR:","999.99","Tarjeta:","Pie propio"))assertTrue(text.contains(expected),expected);
            for(String absent:List.of("RNC:","Caja:","Cajero:","Motivo:","DOCUMENTO DE PRUEBA","Saldo a favor"))assertFalse(text.contains(absent),absent);
            checkGeometry(pdf);
        }
    }
    @Test void absentRefundDoesNotInventCashAndMissingFieldsShrinkTicket() throws Exception {
        var s=fixture(1,"normal");
        var minimal=new SalesReturnPrintDocument(new CompanySnapshot("Empresa",null,null,null,"DO"),s.returnNumber(),s.originalInvoiceNumber(),s.returnDateTime(),s.customer(),null,null,null,null,"USD",s.items(),s.subtotalReturned(),null,null,List.of(),s.totalReturned(),List.of(),null,s.footer(),false);
        try(var pdf=Loader.loadPDF(renderer.render(minimal));var full=Loader.loadPDF(renderer.render(s))) {
            String text=new PDFTextStripper().getText(pdf);assertFalse(text.contains("FORMA DE REEMBOLSO"));assertFalse(text.contains("Efectivo"));
            assertTrue(text.contains("TOTAL DEVUELTO USD:"));assertTrue(pdf.getPage(0).getMediaBox().getHeight()<full.getPage(0).getMediaBox().getHeight());
        }
    }
    @Test void untrustedDescriptionsAndReasonsRemainPlainText() throws Exception {
        var s=fixture(1,"normal");String input="../../dato <script>alert('x')</script> C:\\Windows\\dato";
        var unsafe=new SalesReturnPrintDocument(s.company(),s.returnNumber(),s.originalInvoiceNumber(),s.returnDateTime(),s.customer(),null,null,null,input,s.currency(),List.of(new ReturnedItem(BigDecimal.ONE,input,s.totalReturned())),s.subtotalReturned(),null,null,s.taxesReturned(),s.totalReturned(),s.refundMethods(),null,s.footer(),false);
        try(var pdf=Loader.loadPDF(renderer.render(unsafe))) {
            assertNull(pdf.getDocumentCatalog().getOpenAction());assertNull(pdf.getDocumentCatalog().getNames());for(var page:pdf.getPages())assertTrue(page.getAnnotations().isEmpty());
            assertTrue(new PDFTextStripper().getText(pdf).contains("script"));checkGeometry(pdf);
        }
        assertThrows(IllegalArgumentException.class,()->new ReturnedItem(BigDecimal.ONE,"\u001b@",BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,()->new ReturnedItem(BigDecimal.ZERO,"Producto",BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,()->new CustomerSnapshot(" ",null));
    }
    @Test void visualFixturesAndTechnicalExample() throws Exception {
        Path review=Path.of("target","sales-return-review");Files.createDirectories(review);
        for(String scenario:List.of("1-producto","5-productos","20-productos","descripcion-larga","cliente-largo","empresa-larga","motivo-largo","importe-grande","multipago")) {
            int count=scenario.equals("5-productos")?5:scenario.equals("20-productos")?20:1;
            byte[] bytes=renderer.render(fixture(count,scenario));Files.write(review.resolve(scenario+".pdf"),bytes);
            try(var pdf=Loader.loadPDF(bytes)) {
                checkGeometry(pdf);javax.imageio.ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(pdf).renderImageWithDPI(0,180),"png",review.resolve(scenario+".png").toFile());
            }
        }
        var s=fixture(1,"normal");
        var technical=new SalesReturnPrintDocument(s.company(),s.returnNumber(),s.originalInvoiceNumber(),s.returnDateTime(),s.customer(),s.branch(),s.cashRegister(),s.operator(),s.reason(),s.currency(),s.items(),s.subtotalReturned(),s.discountReturned(),s.surchargeReturned(),s.taxesReturned(),s.totalReturned(),s.refundMethods(),s.totalRefunded(),s.footer(),true);
        Path output=Path.of("output","pdf","devolucion-venta-pos-80mm-prueba.pdf");Files.createDirectories(output.getParent());Files.write(output,renderer.render(technical));
    }
    private void checkGeometry(org.apache.pdfbox.pdmodel.PDDocument pdf) throws Exception {
        new PDFTextStripper(){
            private final Map<String,List<TextPosition>> lines=new HashMap<>();
            @Override protected void processTextPosition(TextPosition p) {
                float margin=SalesReturn80mmPdfRenderer.MARGIN;
                assertEquals(80,getCurrentPage().getMediaBox().getWidth()*25.4/72,0.001);
                assertTrue(p.getXDirAdj()>=margin-0.4);assertTrue(p.getXDirAdj()+p.getWidthDirAdj()<=SalesReturn80mmPdfRenderer.WIDTH-margin+0.5);
                assertTrue(p.getYDirAdj()<=p.getPageHeight()-margin+0.5);
                var row=lines.computeIfAbsent(getCurrentPageNo()+":"+Math.round(p.getYDirAdj()*10),key->new ArrayList<>());
                for(var other:row)assertTrue(p.getXDirAdj()+0.5>=other.getXDirAdj()+other.getWidthDirAdj()||other.getXDirAdj()+0.5>=p.getXDirAdj()+p.getWidthDirAdj(),"Horizontal overlap");
                row.add(p);super.processTextPosition(p);
            }
        }.getText(pdf);
    }
}
