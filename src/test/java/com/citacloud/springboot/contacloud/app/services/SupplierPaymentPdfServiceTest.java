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

class SupplierPaymentPdfServiceTest {
    @Test void generaComprobanteConDatosActualesYSinMarcaDeLaAplicacion() throws Exception{
        byte[] pdf=new SupplierPaymentPdfService().generate(data(lines(2),"APLICADO"));
        writePreview(pdf);
        assertThat(pdf).startsWith("%PDF-".getBytes(StandardCharsets.US_ASCII));
        assertThat(text(pdf)).contains("EMPRESA DEMO, S.R.L.","COMPROBANTE DE PAGO","PG-000025",
            "APLICADO","APLICACIÓN DEL PAGO","Saldo anterior","Aplicado","Restante","FP-000001",
            "RD$ 100.00","RD$ 40.00","RD$ 60.00","RD$ 14,160.00","RD$ 3,000.00")
            .doesNotContainIgnoringCase("ContaCloud");
    }

    @Test void formateaIdentificacionesTelefonosYMoneda(){
        Map<String,Object> parameters=SupplierPaymentPdfService.parameters(data(List.of(),"ANULADO"));
        assertThat(parameters).containsEntry("companyTaxIdentification","124-54367-8")
            .containsEntry("companyPhone","234-567-8665")
            .containsEntry("supplierTaxIdentification","123-45432-4")
            .containsEntry("supplierPhone","809-555-1234")
            .containsEntry("currencyPrefix","RD$").containsEntry("amountFormatted","RD$ 14,160.00")
            .containsEntry("appliedFormatted","RD$ 3,000.00").containsEntry("availableFormatted","RD$ 11,160.00").containsEntry("hasApplications",false)
            .containsEntry("status","ANULADO");
    }

    @Test void mantienePaginacionEnComprobantesExtensos() throws Exception{
        byte[] pdf=new SupplierPaymentPdfService().generate(data(lines(75),"PARCIALMENTE APLICADO"));
        PdfReader reader=new PdfReader(pdf);assertThat(reader.getNumberOfPages()).isGreaterThan(1);
        PdfTextExtractor extractor=new PdfTextExtractor(reader);List<String> pages=new ArrayList<>();
        for(int i=1;i<=reader.getNumberOfPages();i++)pages.add(extractor.getTextFromPage(i));reader.close();
        assertThat(pages).allMatch(page->page.contains("Página"));
        assertThat(pages.subList(1,pages.size())).allMatch(page->page.contains("COMPROBANTE DE PAGO")&&page.contains("PG-000025"));
    }

    @Test void anticipoSinAplicacionesMuestraMensajeEnLugarDeTablaVacia() throws Exception{
        String text=text(new SupplierPaymentPdfService().generate(data(List.of(),"DISPONIBLE")));
        assertThat(text).contains("Este pago no tiene aplicaciones a facturas.","RD$ 11,160.00");
    }

    @Test void saldoHistoricoCumpleLaFormula(){
        SupplierPaymentReportLine line=lines(1).getFirst();
        assertThat(line.saldoAnterior().subtract(line.importe())).isEqualByComparingTo(line.saldoRestante());
    }

    @Test void plantillaNoIncluyeMarcaDeLaAplicacion() throws Exception{
        assertThat(Files.readString(Path.of("src/main/resources/reports/supplier-payment/supplier-payment.jrxml")))
            .doesNotContainIgnoringCase("ContaCloud");
    }

    private static SupplierPaymentReportData data(List<SupplierPaymentReportLine> lines,String status){
        return new SupplierPaymentReportData(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"PG-000025",
            "EMPRESA DEMO, S.R.L.","Empresa Demo","124543678","Av. Principal No. 123, Santiago",
            "2345678665","correo@empresa.com",null,"Distribuidora ABC","123454324","8095551234",
            "pagos@proveedor.com",LocalDate.of(2026,10,10),status,"DOP","Transferencia bancaria",
            "Banco Popular · Cuenta corriente · •••• 1234",null,"TRX-98456321","Pago de facturas de septiembre.",
            "ANULADO".equals(status)?"Operación registrada por error":null,new BigDecimal("14160.00"),
            new BigDecimal("3000.00"),new BigDecimal("11160.00"),"Usuario Administrador",
            OffsetDateTime.parse("2026-10-10T10:40:00-04:00"),lines);
    }
    private static List<SupplierPaymentReportLine> lines(int count){return IntStream.rangeClosed(1,count)
        .mapToObj(i->new SupplierPaymentReportLine("FP-%06d".formatted(i),"PROV-"+i,"05/10/2026","10/10/2026",new BigDecimal("100.00"),new BigDecimal("40.00"),new BigDecimal("60.00"),"RD$")).toList();}
    private static String text(byte[] pdf) throws Exception{PdfReader reader=new PdfReader(pdf);PdfTextExtractor extractor=new PdfTextExtractor(reader);StringBuilder result=new StringBuilder();for(int i=1;i<=reader.getNumberOfPages();i++)result.append(extractor.getTextFromPage(i)).append('\n');reader.close();return result.toString();}
    private static void writePreview(byte[] pdf) throws Exception{String target=System.getProperty("supplierPaymentPdfOutput");if(target==null||target.isBlank())return;Path path=Path.of(target);Files.createDirectories(path.getParent());Files.write(path,pdf);}
}
