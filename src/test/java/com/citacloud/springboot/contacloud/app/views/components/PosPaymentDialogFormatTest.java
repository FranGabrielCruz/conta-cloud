package com.citacloud.springboot.contacloud.app.views.components;

import com.citacloud.springboot.contacloud.app.dto.MetodoPagoPosDto;
import com.citacloud.springboot.contacloud.app.models.TipoMetodoPagoVenta;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test void accionesDelPieTienenTextoVisibleYEtiquetaAccesible() throws Exception {
        PosPaymentDialog dialogo = new PosPaymentDialog(BigDecimal.TEN, "DOP",
                List.of(metodo("Efectivo", TipoMetodoPagoVenta.CASH)), List.of(), pagos -> {});

        Button confirmar = boton(dialogo, "confirmar");
        Button cancelar = boton(dialogo, "cancelar");
        assertEquals("Confirmar cobro", confirmar.getText());
        assertEquals("Cancelar", cancelar.getText());
        assertEquals("Confirmar cobro", confirmar.getElement().getAttribute("aria-label"));
        assertEquals("Cancelar cobro", cancelar.getElement().getAttribute("aria-label"));
    }

    @Test void accionesDelPieCompartenUnGrupoEnElOrdenCorrecto() throws Exception {
        PosPaymentDialog dialogo = new PosPaymentDialog(BigDecimal.TEN, "DOP",
                List.of(metodo("Efectivo", TipoMetodoPagoVenta.CASH)), List.of(), pagos -> {});
        Method actualizar = PosPaymentDialog.class.getDeclaredMethod("actualizarFooter");
        actualizar.setAccessible(true);
        actualizar.invoke(dialogo);

        Div grupo = (Div) campo(dialogo, "accionesFooter");
        List<Component> acciones = grupo.getChildren().toList();
        assertTrue(grupo.hasClassName("cc-pos-payment-action-group"));
        assertEquals(2, acciones.size());
        assertSame(boton(dialogo, "confirmar"), acciones.get(0));
        assertSame(boton(dialogo, "cancelar"), acciones.get(1));
    }

    private static Button boton(PosPaymentDialog dialogo, String nombre) throws Exception {
        return (Button) campo(dialogo, nombre);
    }

    private static Object campo(PosPaymentDialog dialogo, String nombre) throws Exception {
        Field campo = PosPaymentDialog.class.getDeclaredField(nombre);
        campo.setAccessible(true);
        return campo.get(dialogo);
    }

    private static MetodoPagoPosDto metodo(String nombre, TipoMetodoPagoVenta tipo) {
        return new MetodoPagoPosDto(UUID.randomUUID(), nombre.toUpperCase(), nombre, tipo,
                false, false, false, tipo == TipoMetodoPagoVenta.CASH,
                tipo == TipoMetodoPagoVenta.CREDIT_CARD || tipo == TipoMetodoPagoVenta.DEBIT_CARD,
                null, tipo == TipoMetodoPagoVenta.CASH);
    }
}
