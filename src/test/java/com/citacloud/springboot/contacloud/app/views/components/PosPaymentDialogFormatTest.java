package com.citacloud.springboot.contacloud.app.views.components;

import com.citacloud.springboot.contacloud.app.dto.MetodoPagoPosDto;
import com.citacloud.springboot.contacloud.app.models.TipoMetodoPagoVenta;
import com.vaadin.flow.component.button.Button;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PosPaymentDialogFormatTest {
    @Test void utilizaLaMonedaDeLaOperacion() {
        assertEquals("USD 12.50", PosPaymentDialog.formatearDinero("USD", new BigDecimal("12.5")));
        assertEquals("EUR 8.25", PosPaymentDialog.formatearDinero("EUR", new BigDecimal("8.25")));
    }

    @Test void muestraCeroConDosDecimales() {
        assertEquals("DOP 0.00", PosPaymentDialog.formatearDinero("DOP", BigDecimal.ZERO));
    }

    @Test void soportaImportesGrandesYRedondeaLaPresentacion() {
        assertEquals("DOP 125,450.76",
                PosPaymentDialog.formatearDinero("DOP", new BigDecimal("125450.755")));
    }

    @Test void ordenaLosMetodosPrincipalesParaElFlujoDeCaja() {
        MetodoPagoPosDto transferencia = metodo("Transferencia", TipoMetodoPagoVenta.BANK_TRANSFER);
        MetodoPagoPosDto otro = metodo("Otro", TipoMetodoPagoVenta.OTHER);
        MetodoPagoPosDto debito = metodo("Débito", TipoMetodoPagoVenta.DEBIT_CARD);
        MetodoPagoPosDto efectivo = metodo("Efectivo", TipoMetodoPagoVenta.CASH);
        MetodoPagoPosDto credito = metodo("Crédito", TipoMetodoPagoVenta.CREDIT_CARD);

        assertEquals(List.of(efectivo, credito, debito, transferencia),
                PosPaymentDialog.metodosPrincipales(List.of(transferencia, otro, debito, efectivo, credito)));
    }

    @Test void accionesDelPieSonIconOnlyYConEtiquetaAccesible() throws Exception {
        PosPaymentDialog dialogo = new PosPaymentDialog(BigDecimal.TEN, "DOP",
                List.of(metodo("Efectivo", TipoMetodoPagoVenta.CASH)), List.of(), pagos -> {});

        Button confirmar = boton(dialogo, "confirmar");
        Button cancelar = boton(dialogo, "cancelar");
        assertEquals("", confirmar.getText());
        assertEquals("", cancelar.getText());
        assertEquals("Confirmar cobro", confirmar.getElement().getAttribute("aria-label"));
        assertEquals("Cancelar cobro", cancelar.getElement().getAttribute("aria-label"));
    }

    private static Button boton(PosPaymentDialog dialogo, String nombre) throws Exception {
        Field campo = PosPaymentDialog.class.getDeclaredField(nombre);
        campo.setAccessible(true);
        return (Button) campo.get(dialogo);
    }

    private static MetodoPagoPosDto metodo(String nombre, TipoMetodoPagoVenta tipo) {
        return new MetodoPagoPosDto(UUID.randomUUID(), nombre.toUpperCase(), nombre, tipo,
                false, false, false, tipo == TipoMetodoPagoVenta.CASH,
                tipo == TipoMetodoPagoVenta.CREDIT_CARD || tipo == TipoMetodoPagoVenta.DEBIT_CARD,
                null, tipo == TipoMetodoPagoVenta.CASH);
    }
}
