package com.citacloud.springboot.contacloud.app.services;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CobroClienteContratoTest {
    private static final Path ROOT = Path.of("src/main");

    @Test
    void migracionAislaTenantAsignaPermisosAdminYGarantizaIdempotencia() throws Exception {
        String sql = Files.readString(ROOT.resolve("resources/db/migration/V84__implementar_recibos_cobros_clientes.sql"));
        assertTrue(sql.contains("UNIQUE(tenant_id,empresa_id,idempotency_key)"));
        assertTrue(sql.contains("FOREIGN KEY(tenant_id,empresa_id,customer_id)"));
        assertTrue(sql.contains("upper(r.codigo)='ADMINISTRADOR'"));
        assertTrue(sql.contains("'recibos.aplicar'"));
        assertTrue(sql.contains("'recibos.anular'"));
    }

    @Test
    void posEliminaApartadosYUsaPaginacionBackendDeCinco() throws Exception {
        String view = Files.readString(ROOT.resolve("java/com/citacloud/springboot/contacloud/app/views/FacturacionPosView.java"));
        String dialog = Files.readString(ROOT.resolve("java/com/citacloud/springboot/contacloud/app/views/components/RecibosCobrosDialog.java"));
        String service = Files.readString(ROOT.resolve("java/com/citacloud/springboot/contacloud/app/services/CobroClienteService.java"));
        assertFalse(view.contains("menu.addItem(\"Apartados\""));
        assertTrue(view.contains("menu.addItem(\"Recibos / Cobros\""));
        assertTrue(dialog.contains("new PosDialogPagination"));
        assertTrue(dialog.contains("PosDialogPagination.fixed"));
        assertFalse(dialog.contains("invoicePagination = new PosDialogPagination"));
        assertTrue(dialog.contains("setAllRowsVisible(true)"));
        assertTrue(service.contains("PAGE_SIZE=5"));
        assertTrue(dialog.contains("ActionType.COLLECT"));
        assertTrue(dialog.contains("ActionType.PRINT"));
        assertTrue(dialog.contains("ActionType.VOID"));
        assertTrue(dialog.contains("ActionType.CLOSE"));
        assertTrue(dialog.contains("initializeReceivedWhenEmpty"));
        assertFalse(dialog.contains("refreshReceived"));
        String payment=Files.readString(ROOT.resolve("java/com/citacloud/springboot/contacloud/app/views/components/PosPaymentDialog.java"));
        String css=Files.readString(ROOT.resolve("frontend/themes/contacloud/styles.css"));
        assertTrue(payment.contains("aria-pressed"));
        assertTrue(css.contains("--cc-payment-cash"));assertTrue(css.contains("--cc-payment-credit"));
        assertTrue(css.contains("--cc-payment-debit"));assertTrue(css.contains("--cc-payment-transfer"));
        assertTrue(css.contains("--cc-payment-multiple"));
    }

    @Test
    void plantillaPdfCompactaCompilaYGeneraTablasConSaldosHistoricos() throws Exception {
        JasperReport main=compile("/reports/customer-receipt/customer-receipt-compact.jrxml");
        JasperReport applications=compile("/reports/customer-receipt/customer-receipt-applications.jrxml");
        JasperReport methods=compile("/reports/customer-receipt/customer-receipt-methods.jrxml");
        Collection<Map<String,?>> applicationRows=new ArrayList<>();
        applicationRows.add(Map.of("invoice","FAC-000016","previous",new BigDecimal("400.00"),
                "applied",new BigDecimal("100.00"),"remaining",new BigDecimal("300.00")));
        Collection<Map<String,?>> methodRows=new ArrayList<>();
        methodRows.add(Map.of("method","Efectivo","destination","caja1","reference","—","amount",new BigDecimal("100.00")));
        Map<String,Object> params=new HashMap<>();
        params.put("companyName","DEMO");params.put("companyTaxId","124-54367-8");params.put("companyAddress","Av. Principal 123");
        params.put("companyPhone","809-555-0100");params.put("companyEmail","demo@example.com");params.put("companyLogo",null);
        params.put("number","REC-000001");params.put("date","17/10/2026");params.put("customer","Francisco Gabriel Cruz");
        params.put("customerId","031-0543974-3");params.put("currency","DOP");params.put("branch","Sucursal Principal");
        params.put("status","APPLIED");params.put("reference","—");params.put("notes","Adelanto de factura.");params.put("user","admin");
        params.put("received",new BigDecimal("100.00"));params.put("applied",new BigDecimal("100.00"));params.put("available",BigDecimal.ZERO);
        params.put("applicationsReport",applications);params.put("applicationsDataSource",new JRMapCollectionDataSource(applicationRows));
        params.put("methodsReport",methods);params.put("methodsDataSource",new JRMapCollectionDataSource(methodRows));
        Path preview=Path.of("target/customer-receipt-preview.pdf");
        JasperExportManager.exportReportToPdfFile(JasperFillManager.fillReport(main,params,new JREmptyDataSource()),preview.toString());
        assertTrue(Files.size(preview)>1_000);
        String template=Files.readString(ROOT.resolve("resources/reports/customer-receipt/customer-receipt-compact.jrxml"));
        assertTrue(template.contains("FACTURAS APLICADAS"));assertTrue(template.contains("MEDIOS DE PAGO"));
        assertFalse(template.contains("ContaCloud"));
    }

    private JasperReport compile(String resource) throws Exception {
        try(InputStream input=getClass().getResourceAsStream(resource)){assertNotNull(input);return JasperCompileManager.compileReport(input);}
    }
}
