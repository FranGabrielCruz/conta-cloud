package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierCreditNotePdfServiceTest {
    @Test void generaDocumentoEmpresarialSinMarcaDeLaAplicacion() throws Exception{
        SupplierCreditNotePdfService service=new SupplierCreditNotePdfService();
        byte[] pdf=service.generate(data(lines(2),applications()));
        writePreview(pdf);

        assertThat(pdf).startsWith("%PDF-".getBytes(StandardCharsets.US_ASCII));
        String text=text(pdf);
        assertThat(text).contains("EMPRESA DEMO, S.R.L.","NOTA DE CRÉDITO DE PROVEEDOR","NC-000003",
            "Aceite Comercial","ESTADO DEL CRÉDITO","HISTORIAL DE APLICACIONES","FP-000003","RD$ 472.00")
            .doesNotContainIgnoringCase("ContaCloud");
    }

    @Test void formateaDatosDeEmpresaYRepresentaOpcionales(){
        SupplierCreditNoteReportData original=data(lines(1),List.of());
        Map<String,Object> parameters=SupplierCreditNotePdfService.parameters(original);
        assertThat(parameters).containsEntry("companyTaxIdentification","124-54367-8")
            .containsEntry("companyPhone","234-567-8665").containsEntry("supplierTaxIdentification","123-45432-4")
            .containsEntry("currencyPrefix","RD$").containsEntry("relatedInvoice","—")
            .containsEntry("hasApplications",false).containsEntry("hasTaxes",true);
    }

    @Test void noIncluyeUnaColumnaDeCodigoDeProducto() throws Exception{
        SupplierCreditNoteReportLine line=new SupplierCreditNoteReportLine(1,"Servicio sin producto asociado",
            BigDecimal.ONE,new BigDecimal("150.00"),BigDecimal.ZERO,new BigDecimal("150.00"));
        String text=text(new SupplierCreditNotePdfService().generate(data(List.of(line),List.of())));
        assertThat(text).contains("Servicio sin producto asociado").doesNotContain("Código");
    }

    @Test void mantieneEncabezadosPaginacionYFilasEnMultipagina() throws Exception{
        SupplierCreditNotePdfService service=new SupplierCreditNotePdfService();
        byte[] pdf=service.generate(data(lines(70),applications(45)));
        PdfReader reader=new PdfReader(pdf);assertThat(reader.getNumberOfPages()).isGreaterThan(2);
        PdfTextExtractor extractor=new PdfTextExtractor(reader);List<String> pages=new ArrayList<>();
        for(int i=1;i<=reader.getNumberOfPages();i++)pages.add(extractor.getTextFromPage(i));reader.close();
        assertThat(pages).allMatch(page->page.contains("Página"));
        assertThat(pages.stream().filter(page->page.contains("Producto con descripción")).toList()).allMatch(page->page.contains("Descripción"));
        assertThat(pages.stream().filter(page->page.contains("FP-")).toList()).allMatch(page->page.contains("No. proveedor"));
        assertThat(pages.subList(1,pages.size())).allMatch(page->page.contains("NC-000003"));
        assertThat(String.join("\n",pages)).contains("HISTORIAL DE APLICACIONES").doesNotContainIgnoringCase("ContaCloud");
    }

    @Test void laPlantillaNoIncluyeMarcaDeLaAplicacion() throws Exception{
        String template=Files.readString(Path.of("src/main/resources/reports/supplier-credit-note/supplier-credit-note.jrxml"));
        assertThat(template).doesNotContainIgnoringCase("ContaCloud");
    }

    private static SupplierCreditNoteReportData data(List<SupplierCreditNoteReportLine> lines,List<SupplierCreditApplicationReportLine> applications){
        BigDecimal subtotal=lines.stream().map(line->line.total().subtract(line.impuesto())).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal tax=lines.stream().map(SupplierCreditNoteReportLine::impuesto).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal total=subtotal.add(tax);
        return new SupplierCreditNoteReportData(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"NC-000003",
            "EMPRESA DEMO, S.R.L.","Empresa Demo","124543678","Av. Principal No. 123, Santiago",
            "2345678665","correo@empresa.com",null,"Aceite Comercial","123454324",LocalDate.of(2026,10,5),
            "APLICADA","23442343","E340000000001","DOP","Corrección de factura","—",
            "Corrección correspondiente a la factura indicada.",subtotal,BigDecimal.ZERO,tax,total,total,BigDecimal.ZERO,
            "Usuario Administrador",OffsetDateTime.parse("2026-10-05T15:29:00-04:00"),lines,
            List.of(new SupplierCreditTaxReportLine("ITBIS 18%",tax)),applications);
    }

    private static List<SupplierCreditNoteReportLine> lines(int count){
        return IntStream.rangeClosed(1,count).mapToObj(i->new SupplierCreditNoteReportLine(i,
            "Producto con descripción suficientemente extensa para validar la distribución de la fila "+i,
            new BigDecimal("2.00"),new BigDecimal("100.00"),new BigDecimal("36.00"),new BigDecimal("236.00"))).toList();
    }

    private static List<SupplierCreditApplicationReportLine> applications(){
        return List.of(new SupplierCreditApplicationReportLine("05/10/2026","FP-000003","1456",new BigDecimal("472.00")));
    }

    private static List<SupplierCreditApplicationReportLine> applications(int count){
        return IntStream.rangeClosed(1,count).mapToObj(i->new SupplierCreditApplicationReportLine("05/10/2026",
            "FP-%06d".formatted(i),"PROV-"+i,new BigDecimal("10.00"))).toList();
    }

    private static String text(byte[] pdf) throws Exception{
        PdfReader reader=new PdfReader(pdf);PdfTextExtractor extractor=new PdfTextExtractor(reader);StringBuilder result=new StringBuilder();
        for(int i=1;i<=reader.getNumberOfPages();i++)result.append(extractor.getTextFromPage(i)).append('\n');reader.close();return result.toString();
    }

    private static void writePreview(byte[] pdf) throws Exception{
        String target=System.getProperty("supplierCreditNotePdfOutput");if(target==null||target.isBlank())return;
        Path path=Path.of(target);Files.createDirectories(path.getParent());Files.write(path,pdf);
    }
}
