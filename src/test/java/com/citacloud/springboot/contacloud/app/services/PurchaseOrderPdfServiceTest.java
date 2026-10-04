package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.repositories.PurchaseOrderRepository;
import com.citacloud.springboot.contacloud.app.models.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.util.stream.IntStream;

class PurchaseOrderPdfServiceTest {
    @TempDir Path directory;

    @Test void generaYConservaElPdfOficialEnLaRutaMultitenant() throws Exception{
        LocalFileStorageService storage=new LocalFileStorageService(directory.toString());
        PurchaseOrderPdfService service=new PurchaseOrderPdfService(storage,mock(PurchaseOrderRepository.class));
        UUID tenant=UUID.randomUUID(),company=UUID.randomUUID(),order=UUID.randomUUID();
        PurchaseOrderReportData data=data(tenant,company,order);

        byte[] pdf=service.generate(data);String key=service.store(data,pdf);
        String preview=System.getProperty("purchaseOrderPdfOutput");if(preview!=null&&!preview.isBlank()){Path output=Path.of(preview);Files.createDirectories(output.getParent());Files.write(output,pdf);}

        assertThat(pdf).startsWith("%PDF-".getBytes(StandardCharsets.US_ASCII));
        assertThat(key).isEqualTo("tenants/"+tenant+"/companies/"+company+"/purchase-orders/2026/"+order+".pdf");
        assertThat(storage.load(key)).contains(pdf);
        assertThatThrownBy(()->storage.storePdf("../orden.pdf",pdf)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void separaContactoTelefonoYCamposOpcionales(){
        PurchaseOrderReportData data=data(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        Map<String,Object> parameters=PurchaseOrderPdfService.parameters(data);
        assertThat(parameters).containsEntry("supplierContactName","Juan Pérez").containsEntry("supplierPhone","809-111-1111")
            .containsEntry("companyTaxIdentification","101-00000-1").containsEntry("companyPhone","809-000-0000")
            .containsEntry("supplierTaxIdentification","130-00000-1")
            .containsEntry("currencyCode","DOP").containsEntry("totalLabel","TOTAL DOP").containsEntry("notes","Entregar en almacén.")
            .containsEntry("subtotal",new BigDecimal("200.00")).containsEntry("discount",BigDecimal.ZERO)
            .containsEntry("tax",new BigDecimal("36.00")).containsEntry("total",new BigDecimal("236.00"));
    }

    @Test void formateaRncYTelefonosSinModificarValoresNoDominicanos(){
        assertThat(PurchaseOrderPdfService.formatTaxIdentification("124543678")).isEqualTo("124-54367-8");
        assertThat(PurchaseOrderPdfService.formatTaxIdentification("124-54367-8")).isEqualTo("124-54367-8");
        assertThat(PurchaseOrderPdfService.formatTaxIdentification("ABC-123")).isEqualTo("ABC-123");
        assertThat(PurchaseOrderPdfService.formatPhone("2345678665")).isEqualTo("234-567-8665");
        assertThat(PurchaseOrderPdfService.formatPhone("234-567-8665")).isEqualTo("234-567-8665");
        assertThat(PurchaseOrderPdfService.formatPhone("+1 809 555 0101")).isEqualTo("+1 809 555 0101");
    }

    @Test void conservaNulosEnCamposOpcionalesYOcultaNotasVacias(){
        PurchaseOrderReportData original=data(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        PurchaseOrderReportData empty=new PurchaseOrderReportData(original.tenantId(),original.companyId(),original.orderId(),original.orderNumber(),
            original.companyTradeName(),null,original.companyTaxIdentification(),null,null,null,null,original.supplierName(),original.supplierTaxIdentification(),
            null,null,null,original.orderDate(),null,null,original.currencyCode(),null,null,"   ",original.subtotal(),original.discount(),original.tax(),original.total(),
            original.issuedByDisplayName(),original.issuedAt(),original.lines());
        Map<String,Object> parameters=PurchaseOrderPdfService.parameters(empty);
        assertThat(parameters).containsEntry("companyAddress",null).containsEntry("companyPhone",null).containsEntry("companyEmail",null)
            .containsEntry("notes",null).containsEntry("supplierContactName","—").containsEntry("supplierPhone","—")
            .containsEntry("paymentTermName","—").containsEntry("reference","—");
    }

    @Test void resuelveNombreDelEmisorYFallbackDeUsuario(){
        UUID tenant=UUID.randomUUID(),company=UUID.randomUUID();
        Usuario named=new Usuario(tenant,company,"gperez","Gabriel","Pérez","hash");
        Usuario fallback=new Usuario(tenant,company,"admin"," "," ","hash");
        assertThat(PurchaseOrderService.issuedByDisplayName(named)).isEqualTo("Gabriel Pérez");
        assertThat(PurchaseOrderService.issuedByDisplayName(fallback)).isEqualTo("admin");
    }

    @Test void mapeaContactoYTelefonoDelProveedorEnCamposDiferentes(){
        Proveedor supplier=new Proveedor(UUID.randomUUID(),UUID.randomUUID(),"SUP-1","Proveedor",null,"123","123",TipoProveedor.NATIONAL,
            "809-111-1111","ventas@test.com",null,null,null,"Juan Pérez","809-222-2222",null,true,UUID.randomUUID());
        assertThat(PurchaseOrderService.supplierContactName(supplier)).isEqualTo("Juan Pérez");
        assertThat(PurchaseOrderService.supplierPhone(supplier)).isEqualTo("809-111-1111");
    }

    @Test void mantieneEncabezadosPaginacionYTotalesEnMultipagina() throws Exception{
        PurchaseOrderReportData original=data(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        List<PurchaseOrderReportLine> lines=IntStream.rangeClosed(1,60).mapToObj(i->new PurchaseOrderReportLine(i,"PRD-"+i,
            "Producto con descripción suficientemente extensa para validar el crecimiento de la fila número "+i,"Unidad (und)",
            new BigDecimal("2.00"),new BigDecimal("100.00"),BigDecimal.ZERO,new BigDecimal("36.00"),new BigDecimal("236.00"))).toList();
        PurchaseOrderReportData multipage=new PurchaseOrderReportData(original.tenantId(),original.companyId(),original.orderId(),original.orderNumber(),
            original.companyTradeName(),original.companyLegalName(),original.companyTaxIdentification(),original.companyAddress(),original.companyPhone(),
            original.companyEmail(),null,original.supplierName(),original.supplierTaxIdentification(),original.supplierContactName(),original.supplierPhone(),
            original.supplierEmail(),original.orderDate(),original.expectedDeliveryDate(),original.branchName(),original.currencyCode(),original.paymentTermName(),
            original.reference(),original.notes(),original.subtotal(),original.discount(),original.tax(),original.total(),original.issuedByDisplayName(),original.issuedAt(),lines);
        PurchaseOrderPdfService service=new PurchaseOrderPdfService(new LocalFileStorageService(directory.toString()),mock(PurchaseOrderRepository.class));
        byte[] bytes=service.generate(multipage);String preview=System.getProperty("purchaseOrderPdfMultipageOutput");if(preview!=null&&!preview.isBlank())Files.write(Path.of(preview),bytes);
        PdfReader reader=new PdfReader(bytes);assertThat(reader.getNumberOfPages()).isGreaterThan(2);PdfTextExtractor extractor=new PdfTextExtractor(reader);
        List<String> pages=new ArrayList<>();for(int i=1;i<=reader.getNumberOfPages();i++)pages.add(extractor.getTextFromPage(i));reader.close();
        assertThat(pages).allMatch(page->page.contains("Página"));
        assertThat(pages.stream().filter(page->page.contains("PRD-")).toList()).allMatch(page->page.contains("Código"));
        assertThat(pages.subList(1,pages.size())).allMatch(page->page.contains("ORDEN DE COMPRA")&&page.contains("OC-000001"));
        String text=String.join("\n",pages);assertThat(text).containsOnlyOnce("TOTAL DOP").containsOnlyOnce("NOTAS / OBSERVACIONES")
            .doesNotContain("Empresa Demo · Orden");
    }

    @Test void laPlantillaNoIncluyeMarcaDeLaAplicacion() throws Exception{
        String template=Files.readString(Path.of("src/main/resources/reports/purchase-order/purchase-order.jrxml"));
        assertThat(template).doesNotContainIgnoringCase("ContaCloud");
    }

    private static PurchaseOrderReportData data(UUID tenant,UUID company,UUID order){
        return new PurchaseOrderReportData(tenant,company,order,"OC-000001","Empresa Demo","Empresa Demo, SRL","101000001",
            "Santo Domingo","809-000-0000","compras@demo.test",null,"Proveedor Demo","130000001","Juan Pérez","809-111-1111","ventas@proveedor.test",
            LocalDate.of(2026,10,3),LocalDate.of(2026,10,10),"Sucursal Principal","DOP","Crédito 30 días","REQ-100","Entregar en almacén.",
            new BigDecimal("200.00"),BigDecimal.ZERO,new BigDecimal("36.00"),new BigDecimal("236.00"),"Administrador",OffsetDateTime.parse("2026-10-03T12:00:00-04:00"),
            List.of(new PurchaseOrderReportLine(1,"PRD-001","Producto de prueba","Unidad (und)",new BigDecimal("2"),new BigDecimal("100"),BigDecimal.ZERO,new BigDecimal("36"),new BigDecimal("236"))));
    }
}
