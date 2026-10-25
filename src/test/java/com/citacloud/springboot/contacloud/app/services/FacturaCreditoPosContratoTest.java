package com.citacloud.springboot.contacloud.app.services;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class FacturaCreditoPosContratoTest {
    private static final Path ROOT=Path.of("src/main");

    @Test void migracionPermiteCreditoSinCobroYCreaPermisosParaAdministrador() throws Exception {
        String sql=Files.readString(ROOT.resolve("resources/db/migration/V86__implementar_factura_venta_credito.sql"));
        assertTrue(sql.contains("payment_condition='Crédito' AND received_amount=0 AND change_amount=0"));
        assertTrue(sql.contains("facturas.facturar_credito"));
        assertTrue(sql.contains("facturas.exceder_limite_credito"));
        assertTrue(sql.contains("upper(r.codigo)='ADMINISTRADOR'"));
    }

    @Test void backendSeparaCreditoDeLosMediosDePagoYGeneraCxcAtomicamente() throws Exception {
        String service=Files.readString(ROOT.resolve("java/com/citacloud/springboot/contacloud/app/services/FacturacionPosService.java"));
        assertTrue(service.contains("if(input.ventaCredito())"));
        assertTrue(service.contains("Una factura a crédito no puede incluir pagos inmediatos."));
        assertTrue(service.contains("cuentasCobrar.crearCuenta"));
        assertTrue(service.contains("if(!input.ventaCredito())"));
        assertTrue(service.contains("clientes.bloquear"));
    }

    @Test void modalUsaCheckboxYOcultaElCobroEnModoCredito() throws Exception {
        String dialog=Files.readString(ROOT.resolve("java/com/citacloud/springboot/contacloud/app/views/components/PosPaymentDialog.java"));
        String styles=Files.readString(ROOT.resolve("frontend/themes/contacloud/styles.css"));
        assertTrue(dialog.contains("new Checkbox(\"Marcar a crédito\")"));
        assertTrue(dialog.contains("private void mostrarCredito()"));
        assertTrue(dialog.contains("case CREDIT_CARD -> \"T. crédito\""));
        assertTrue(dialog.contains("case DEBIT_CARD -> \"T. débito\""));
        assertTrue(dialog.contains("El cliente excede el límite de crédito permitido."));
        assertTrue(dialog.contains("confirmar.setEnabled(!creditoBloqueado())"));
        assertTrue(dialog.contains("new Button(\"Cancelar\""));
        assertTrue(styles.contains("overflow-y: auto"));
        assertTrue(styles.contains("white-space: normal"));
        assertFalse(styles.contains(".cc-pos-payment-dialog::part(content) { max-height: calc(100vh - 180px); overflow: hidden; }"));
    }
}
