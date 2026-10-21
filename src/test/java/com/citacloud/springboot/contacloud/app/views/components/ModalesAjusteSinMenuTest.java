package com.citacloud.springboot.contacloud.app.views.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ModalesAjusteSinMenuTest {
    private static final Path COMPONENTES = Path.of("src", "main", "java", "com", "citacloud",
            "springboot", "contacloud", "app", "views", "components");

    @Test
    void descuentoRenderizaSoloSeleccionProductoCantidadEImporte() throws IOException {
        verificarTabla(COMPONENTES.resolve("DescuentoVentaDialog.java"));
    }

    @Test
    void recargoRenderizaSoloSeleccionProductoCantidadEImporte() throws IOException {
        verificarTabla(COMPONENTES.resolve("RecargoVentaDialog.java"));
    }

    private static void verificarTabla(Path archivo) throws IOException {
        String fuente = Files.readString(archivo);
        assertEquals(2, ocurrencias(fuente, "grid.addComponentColumn"),
                "La tabla debe tener únicamente las columnas de checkbox y producto basadas en componentes.");
        assertEquals(2, ocurrencias(fuente, "grid.addColumn"),
                "La tabla debe tener únicamente las columnas Cant. e Importe basadas en texto.");
        assertFalse(fuente.contains("setWidth(\"54px\")"),
                "La celda de selección no debe recortar el checkbox y generar puntos suspensivos.");
        for (String clave : new String[] { "seleccion", "producto", "cantidad", "importe" }) {
            assertEquals(1, ocurrencias(fuente, "setKey(\"" + clave + "\")"),
                    "Debe existir exactamente una columna DOM identificada como " + clave + ".");
        }
        assertEquals(1, ocurrencias(fuente,
                "setWidth(\"calc(1lh + var(--vaadin-padding-inline-container) + var(--vaadin-padding-inline-container) + 4px)\")"),
                "La celda del checkbox debe ajustarse a su contenido y al padding real de Vaadin.");
        for (String elementoProhibido : new String[] {
                "DropdownMenu", "ContextMenu", "ActionMenu", "MoreVertical", "MoreHorizontal",
                "VaadinIcon.ELLIPSIS", "setHeader(\"Acciones\")"
        }) {
            assertFalse(fuente.contains(elementoProhibido),
                    () -> "El componente no debe contener el elemento de menú: " + elementoProhibido);
        }
    }

    private static int ocurrencias(String texto, String patron) {
        int cantidad = 0;
        int posicion = 0;
        while ((posicion = texto.indexOf(patron, posicion)) >= 0) {
            cantidad++;
            posicion += patron.length();
        }
        return cantidad;
    }
}
