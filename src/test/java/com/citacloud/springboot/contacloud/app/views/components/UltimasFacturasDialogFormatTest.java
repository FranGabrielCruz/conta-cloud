package com.citacloud.springboot.contacloud.app.views.components;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class UltimasFacturasDialogFormatTest {
    private static final Path ROOT=Path.of("").toAbsolutePath();
    @Test void mantieneConsultaReimpresionYTecladoSinDevoluciones() throws Exception {
        String source=Files.readString(ROOT.resolve("src/main/java/com/citacloud/springboot/contacloud/app/views/components/UltimasFacturasDialog.java"));
        assertThat(source).contains("Buscar por cliente, RNC/Cédula o ID de factura...","Ver factura","Reimprimir factura",KeyMarker.ENTER);
        assertThat(source).doesNotContain("Devolver factura","Crear devolución");
    }
    @Test void f7AbreElDialogoRealDesdeElPos() throws Exception {
        String source=Files.readString(ROOT.resolve("src/main/java/com/citacloud/springboot/contacloud/app/views/FacturacionPosView.java"));
        assertThat(source).contains("this::dialogoUltimasFacturas, Key.F7","Ver últimas facturas (F7)","new UltimasFacturasDialog");
        assertThat(source).doesNotContain("pendiente(\"Últimas ventas\")");
    }
    @Test void informacionGeneralEsCompactaYResponsiveEnCuatroDosYUnaColumnas() throws Exception {
        String dialog=Files.readString(ROOT.resolve("src/main/java/com/citacloud/springboot/contacloud/app/views/components/UltimasFacturasDialog.java"));
        String css=Files.readString(ROOT.resolve("src/main/frontend/themes/contacloud/styles.css"));
        assertThat(dialog).contains("cabecera.addClassName(\"cc-latest-invoice-general\")")
                .contains(".field(\"Fecha/Hora\"").contains(".field(\"Cliente\"").contains(".field(\"RNC/Cédula\"")
                .contains(".field(\"Estado\"").contains(".field(\"Moneda\"").contains(".field(\"Condición de pago\"")
                .contains(".field(\"Vendedor\"");
        assertThat(css).contains(".cc-latest-invoice-general .cc-detail-grid")
                .contains("grid-template-columns: repeat(4, minmax(0, 1fr));")
                .contains("grid-template-columns: repeat(2, minmax(0, 1fr));")
                .contains("grid-template-columns: minmax(0, 1fr);")
                .contains("column-gap: 24px;").contains("row-gap: 18px;").contains("text-overflow: ellipsis;");
    }
    private static final class KeyMarker {private static final String ENTER="Key.ENTER";}
}
