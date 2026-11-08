package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.repositories.NotaCreditoVentaConsultaRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.*;
import org.apache.pdfbox.rendering.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotaCreditoVentaPdfServiceTest {
    final NotaCreditoVentaConsultaRepository repo=mock(NotaCreditoVentaConsultaRepository.class);
    final NotaCreditoVentaPdfService pdf=new NotaCreditoVentaPdfService(repo);
    static NotaCreditoVentaDocumento fixture(int count){
        var rows=new ArrayList<NotaCreditoVentaDocumento.Linea>();for(int i=0;i<count;i++)rows.add(new NotaCreditoVentaDocumento.Linea(UUID.randomUUID(),"P-"+i,"Producto "+(i+1)+" — Descripción extensa: café, piña, pingüino, ¿cantidad?, ¡revisión! "+(count>1?"Material de oficina y servicios empresariales con detalle adicional para verificar el ajuste automático del texto. ":""),"UND",BigDecimal.ONE,new BigDecimal(count==1?"12.00":"9999999.99"),new BigDecimal(count==1?"12.00":"9999999.99"),BigDecimal.ZERO,BigDecimal.ZERO,count==1?"itbis 18":"Impuesto diverso",new BigDecimal(count==1?"18":"12"),new BigDecimal(count==1?"2.16":"1200000"),new BigDecimal(count==1?"14.16":"11199999.99")));
        BigDecimal total=new BigDecimal(count==1?"14.16":"11199999.99").multiply(BigDecimal.valueOf(count));
        return new NotaCreditoVentaDocumento(UUID.randomUUID(),"NCV-PRUEBA-000012",LocalDate.of(2026,11,8),LocalDateTime.of(2026,11,8,9,15),"Operador de prueba","FINANCIAL",null,"CONFIRMED",UUID.randomUUID(),UUID.randomUUID(),"Comercial Álvarez y Asociados, S.R.L.","00112345678",null,"Servicios Comerciales Pérez & Hijos","124543678","Avenida de los Próceres 123, Edificio Empresarial, Santo Domingo","8095551234","FAC-PRUEBA-000017",LocalDate.of(2026,11,5),null,null,"Crédito",count==1?"DOP":"USD","Error de facturación","Crédito autorizado. Datos de prueba sin efecto financiero real.","Documento de prueba. Verificar distribución y lectura de textos largos.",rows,new BigDecimal(count==1?"12.00":"9999999.99").multiply(BigDecimal.valueOf(count)),BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal(count==1?"2.16":"1200000").multiply(BigDecimal.valueOf(count)),total,count==1?new BigDecimal("1000"):total,total,count==1?new BigDecimal("985.84"):BigDecimal.ZERO,BigDecimal.ZERO,"Servicios Comerciales Pérez e Hijos, S.R.L.","info@prueba.test",testLogo());
    }
    @Test void generaA4SinMarcaDelSistemaConDatosHistoricosYCamposOpcionalesOmitidos()throws Exception{
        var d=fixture(1);byte[] bytes=pdf.render(d);try(var doc=Loader.loadPDF(bytes)){
            assertThat(doc.getPage(0).getRotation()%180==90?doc.getPage(0).getMediaBox().getHeight():doc.getPage(0).getMediaBox().getWidth()).isEqualTo(842f);String text=new PDFTextStripper().getText(doc);
            assertThat(text).contains("NOTA DE CRÉDITO","NCV-PRUEBA-000012","FAC-PRUEBA-000017","DOP","ITBIS 18%","APLICACIÓN DEL CRÉDITO","124-54367-8","809-555-1234","CLIENTE","DOCUMENTO AFECTADO","Saldo pendiente","1,000.00","14.16","985.84");assertThat(text).doesNotContain("ContaCloud","NCF afectado:","NCF de la nota:","CLIENTE / DOCUMENTO AFECTADO");
            assertThat(doc.getDocumentCatalog().getOpenAction()).isNull();assertThat(doc.getPage(0).getAnnotations()).isEmpty();
        }
        save("NotaCredito-Parcial-PRUEBA",bytes);
    }
    @Test void generaMultipaginaSinClippingConEncabezadosYTotalesUnaSolaVez()throws Exception{
        byte[] bytes=pdf.render(fixture(70));try(var doc=Loader.loadPDF(bytes)){
            assertThat(doc.getNumberOfPages()).isGreaterThan(2);String text=new PDFTextStripper().getText(doc);assertThat(text.split("TOTAL USD",-1)).hasSize(2);
            assertThat(text).contains("Producto 70","Página 1 de ");
            for(int i=1;i<doc.getNumberOfPages();i++){var stripper=new PDFTextStripper();stripper.setStartPage(i+1);stripper.setEndPage(i+1);assertThat(stripper.getText(doc)).contains("NOTA DE CRÉDITO","NCV-PRUEBA-000012");}
            var bounds=new PDFTextStripper(){@Override protected void processTextPosition(TextPosition p){assertThat(p.getXDirAdj()).isGreaterThanOrEqualTo(29);assertThat(p.getXDirAdj()+p.getWidthDirAdj()).isLessThanOrEqualTo(813);assertThat(p.getYDirAdj()).isLessThanOrEqualTo(577);super.processTextPosition(p);}};bounds.getText(doc);
        }
        save("NotaCredito-Multipagina-PRUEBA",bytes);
    }
    @Test void snapshotSeSerializaYReimprimirSoloConsultaElDocumento()throws Exception{
        var d=fixture(1);var json=JsonMapper.builder().build();assertThat(json.readValue(json.writeValueAsString(d),NotaCreditoVentaDocumento.class)).usingRecursiveComparison().isEqualTo(d);
        when(repo.documento(d.id())).thenReturn(d);pdf.generar(d.id());pdf.generar(d.id());verify(repo,times(2)).documento(d.id());verifyNoMoreInteractions(repo);
    }
    @Test void nombreDeArchivoNoAdmiteTraversal()throws Exception{
        var d=mock(NotaCreditoVentaDocumento.class);when(d.numero()).thenReturn("../../fuera");UUID id=UUID.randomUUID();when(repo.documento(id)).thenReturn(d);assertThatThrownBy(()->pdf.generar(id)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void etiquetasNoRepitenTasaYSoportanImpuestosDiferentes(){
        assertThat(NotaCreditoVentaPdfService.taxLabel("itbis 18",new BigDecimal("18"))).isEqualTo("ITBIS 18%");
        assertThat(NotaCreditoVentaPdfService.taxLabel("IVA 18.00%",new BigDecimal("18"))).isEqualTo("IVA 18%");
        assertThat(NotaCreditoVentaPdfService.taxLabel("Impuesto municipal 2%",new BigDecimal("2"))).isEqualTo("IMPUESTO MUNICIPAL 2%");
        assertThat(NotaCreditoVentaPdfService.taxLabel("Impuesto 2026",new BigDecimal("12"))).isEqualTo("IMPUESTO 2026 12%");
    }
    @Test void opcionalesNcfMultiplesImpuestosYSaldoAFavorSinInventarAplicacion()throws Exception{
        var json=JsonMapper.builder().build();var d=fixture(2);
        @SuppressWarnings("unchecked") Map<String,Object> values=json.readValue(json.writeValueAsString(d),Map.class);
        values.put("empresa","Empresa Comercial y Servicios de Ingeniería Internacional con Nombre Extenso para Verificar Encabezado");values.put("cliente","Cliente Comercial con Nombre Extenso y Asociados para Revisar el Ajuste Automático de las Columnas y sus Límites");
        values.put("direccionEmpresa",null);values.put("telefono",null);values.put("identificacion",null);values.put("ncfFactura","B0200000017");values.put("ncf","B0400000012");values.put("devolucion","DEV-PRUEBA-2");
        values.put("saldoAnterior",null);values.put("aplicadoCxC",BigDecimal.ZERO);values.put("saldoPosterior",null);values.put("creditoCliente",d.total());
        var rows=new ArrayList<Map<String,Object>>();for(int i=0;i<2;i++){
            @SuppressWarnings("unchecked") Map<String,Object> row=json.readValue(json.writeValueAsString(d.lineas().get(i)),Map.class);
            row.put("impuestoNombre",i==0?"itbis 18":"Impuesto municipal 2%");row.put("tasa",i==0?18:2);rows.add(row);
        }values.put("lineas",rows);var variant=json.convertValue(values,NotaCreditoVentaDocumento.class);byte[] bytes=pdf.render(variant);
        try(var doc=Loader.loadPDF(bytes)){
            String text=new PDFTextStripper().getText(doc);assertThat(text).contains("B0200000017","B0400000012","Documento origen: Devolución DEV-PRUEBA-2","ITBIS 18%","IMPUESTO MUNICIPAL 2%","APLICACIÓN DEL CRÉDITO","Saldo a favor del cliente (USD)","22,399,999.98");
            assertThat(text).doesNotContain("18 18%","2% 2%","RNC/Cédula:","Tel.:","Saldo anterior","Crédito aplicado","Saldo pendiente");assertBounds(doc);
        }save("NotaCredito-SaldoFavor-PRUEBA",bytes);
    }
    @Test void textoMaliciosoSeTrataComoContenidoSinAccionesEjecutables()throws Exception{
        var json=JsonMapper.builder().build();@SuppressWarnings("unchecked") Map<String,Object> values=json.readValue(json.writeValueAsString(fixture(1)),Map.class);
        values.put("descripcion","<script>alert('prueba')</script> javascript:alert(1) /Launch /JavaScript");
        try(var doc=Loader.loadPDF(pdf.render(json.convertValue(values,NotaCreditoVentaDocumento.class)))){
            assertThat(doc.getDocumentCatalog().getOpenAction()).isNull();assertThat(doc.getDocumentCatalog().getNames()==null?null:doc.getDocumentCatalog().getNames().getJavaScript()).isNull();assertThat(doc.getDocumentCatalog().getCOSObject().containsKey(org.apache.pdfbox.cos.COSName.AA)).isFalse();for(var page:doc.getPages())assertThat(page.getAnnotations()).isEmpty();assertBounds(doc);
        }
    }
    static void assertBounds(org.apache.pdfbox.pdmodel.PDDocument doc)throws Exception{
        var bounds=new PDFTextStripper(){@Override protected void processTextPosition(TextPosition p){assertThat(p.getXDirAdj()).isGreaterThanOrEqualTo(29);assertThat(p.getXDirAdj()+p.getWidthDirAdj()).isLessThanOrEqualTo(813);assertThat(p.getYDirAdj()).isBetween(15f,577f);super.processTextPosition(p);}};bounds.getText(doc);
    }
    static byte[] testLogo(){
        try{var image=new java.awt.image.BufferedImage(156,100,java.awt.image.BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setColor(new java.awt.Color(51,65,85));g.fillRect(0,0,156,100);g.setColor(java.awt.Color.WHITE);g.setFont(new java.awt.Font("SansSerif",java.awt.Font.BOLD,19));g.drawString("EMPRESA",24,58);g.dispose();var out=new java.io.ByteArrayOutputStream();ImageIO.write(image,"PNG",out);return out.toByteArray();}catch(java.io.IOException ex){throw new java.io.UncheckedIOException(ex);}
    }
    @Test void mismaGeometriaYLogoQuePlantillaOficialDeOrdenCompra()throws Exception{
        var d=fixture(1);var data=new PurchaseOrderReportData(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"OC-PRUEBA-000012",d.empresa(),d.empresaLegal(),d.rnc(),d.direccionEmpresa(),d.telefono(),d.correoEmpresa(),d.logoEmpresa(),d.cliente(),d.identificacion(),null,null,null,d.fecha(),null,"Sucursal de prueba",d.moneda(),"Crédito",null,null,d.subtotal(),d.descuento(),d.impuesto(),d.total(),d.emitidaPor(),d.emitidaEn().atOffset(ZoneOffset.ofHours(-4)),List.of(new PurchaseOrderReportLine(1,"P-1",d.lineas().getFirst().descripcion(),"UND",BigDecimal.ONE,new BigDecimal("12"),BigDecimal.ZERO,new BigDecimal("2.16"),new BigDecimal("14.16"))));
        var order=new PurchaseOrderPdfService(mock(LocalFileStorageService.class),mock(com.citacloud.springboot.contacloud.app.repositories.PurchaseOrderRepository.class)).generate(data);
        save("OrdenCompra-Referencia-PRUEBA",order);
        try(var a=Loader.loadPDF(order);var b=Loader.loadPDF(pdf.render(d))){
            assertThat(b.getPage(0).getMediaBox().getWidth()).isEqualTo(a.getPage(0).getMediaBox().getWidth());assertThat(b.getPage(0).getMediaBox().getHeight()).isEqualTo(a.getPage(0).getMediaBox().getHeight());assertThat(b.getPage(0).getRotation()).isEqualTo(a.getPage(0).getRotation());
            assertThat(b.getPage(0).getResources().getXObjectNames()).anyMatch(n->{try{return b.getPage(0).getResources().isImageXObject(n);}catch(Exception e){return false;}});
            var left=new PDFRenderer(a).renderImageWithDPI(0,72,ImageType.RGB);var right=new PDFRenderer(b).renderImageWithDPI(0,72,ImageType.RGB);
            var sideBySide=new java.awt.image.BufferedImage(1684,595,java.awt.image.BufferedImage.TYPE_INT_RGB);var g=sideBySide.createGraphics();g.drawImage(left,0,0,null);g.drawImage(right,842,0,null);g.dispose();ImageIO.write(sideBySide,"PNG",Path.of("output/pdf/Comparacion-OC-NotaCredito-100.png").toFile());
        }
    }
    @Test void consumidorFinalSinIdentificacionNoMuestraClienteNiSaldoInventados()throws Exception{
        var json=JsonMapper.builder().build();@SuppressWarnings("unchecked") Map<String,Object> data=json.readValue(json.writeValueAsString(fixture(1)),Map.class);
        data.put("clienteId",null);data.put("cliente","Consumidor Final");data.put("identificacion",null);data.put("saldoAnterior",null);data.put("aplicadoCxC",BigDecimal.ZERO);data.put("saldoPosterior",null);data.put("creditoCliente",BigDecimal.ZERO);data.put("logoEmpresa",null);
        try(var doc=Loader.loadPDF(pdf.render(json.convertValue(data,NotaCreditoVentaDocumento.class)))){
            assertThat(new PDFTextStripper().getText(doc)).contains("Consumidor Final","Crédito sobre factura","no genera saldo a favor ni reembolso automático").doesNotContain("RNC/Cédula:","Saldo a favor del cliente");assertBounds(doc);
        }
    }
    @Test void notaAnuladaSeIdentificaSinModificarImportesHistoricos()throws Exception{
        var original=fixture(1);var voided=original.conEstado("VOIDED");byte[] bytes=pdf.render(voided);
        try(var doc=Loader.loadPDF(bytes)){String text=new PDFTextStripper().getText(doc);assertThat(text).contains("ANULADA","documento sin vigencia","emisión original","14.16","985.84");assertBounds(doc);}assertThat(original.estado()).isEqualTo("CONFIRMED");assertThat(voided.total()).isEqualTo(original.total());save("NotaCredito-Anulada-PRUEBA",bytes);
    }
    static void save(String name,byte[] bytes)throws Exception{
        Path dir=Path.of("output/pdf");Files.createDirectories(dir);Files.write(dir.resolve(name+".pdf"),bytes);
        try(var doc=Loader.loadPDF(bytes)){var renderer=new PDFRenderer(doc);for(int i=0;i<doc.getNumberOfPages();i++)ImageIO.write(renderer.renderImageWithDPI(i,96,ImageType.RGB),"PNG",dir.resolve(name+"-"+(i+1)+".png").toFile());}
    }
}
