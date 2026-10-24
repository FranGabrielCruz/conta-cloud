package com.citacloud.springboot.contacloud.app.views.components;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.*;
import org.junit.jupiter.api.Test;

class CargarCotizacionPosContractTest {
    private static String fuente(String ruta) throws Exception {
        return Files.readString(Path.of("src/main/java/com/citacloud/springboot/contacloud/app/"+ruta));
    }

    @Test void eliminaPlaceholderYEjecutaCargaReal() throws Exception {
        String vista=fuente("views/FacturacionPosView.java");
        assertThat(vista).doesNotContain("La carga de la cotización estará disponible próximamente.");
        assertThat(vista).contains("cotizacionPosService.cargar(token,terminal.almacenId(),id)");
        assertThat(vista).contains("Hay una venta en curso. Guárdela o límpiela antes de cargar una cotización.");
    }

    @Test void accionEsUnicaCompactaYPaginadaEnBackend() throws Exception {
        String dialogo=fuente("views/components/CargarCotizacionDialog.java");
        assertThat(dialogo).contains("ButtonSize.GRID_ACTION").contains("setWidth(\"44px\")")
            .contains("setResizable(false)").doesNotContain("MenuBar").doesNotContain("ContextMenu");
        assertThat(dialogo).contains("service.buscarParaPos(buscar.getValue(),desde.getValue(),hasta.getValue(),estado.getValue()");
    }

    @Test void conservaTrazabilidadYConvierteSoloAlConfirmar() throws Exception {
        String vista=fuente("views/FacturacionPosView.java");
        String facturacion=fuente("services/FacturacionPosService.java");
        String guardada=fuente("services/VentaGuardadaService.java");
        assertThat(vista).contains("cotizacionOrigenId").contains("cotizacionLineaOrigenId");
        assertThat(guardada).contains("venta.cotizacionOrigen(input.cotizacionOrigenId())")
            .contains("lineaGuardada.cotizacionLineaOrigen");
        assertThat(facturacion).contains("registrarConversionCotizacion")
            .contains("sales_quote_line_conversion").contains("cotizaciones.bloquear");
    }

    @Test void normalizaLaCantidadPersistidaSoloParaSuPresentacionEnElPos() throws Exception {
        String vista=fuente("views/FacturacionPosView.java");
        assertThat(vista).contains("cantidad.setValue(PosNumberFormatter.quantityInput(fila.cantidad))")
            .contains("private static String formatoCantidad(BigDecimal valor)")
            .contains("return PosNumberFormatter.quantity(valor)");
        assertThat(PosNumberFormatter.quantity(new java.math.BigDecimal("1.0000"))).isEqualTo("1");
        assertThat(PosNumberFormatter.quantity(new java.math.BigDecimal("1.5000"))).isEqualTo("1.5");
    }

    @Test void recargoMonetarioConservaMonedaDinamicaYFormatoDeDosDecimales() throws Exception {
        String dialogo=fuente("views/components/RecargoVentaDialog.java");
        assertThat(dialogo).contains("new Span(opciones.moneda())")
            .contains("PosNumberFormatter.moneyInput(actual)")
            .contains("PosNumberFormatter.money(valor)");
        assertThat(PosNumberFormatter.money(new java.math.BigDecimal("200.000000"))).isEqualTo("200.00");
    }
}
