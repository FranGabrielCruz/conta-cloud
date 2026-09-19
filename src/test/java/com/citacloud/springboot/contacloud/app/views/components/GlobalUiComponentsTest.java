package com.citacloud.springboot.contacloud.app.views.components;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class GlobalUiComponentsTest {
    @Test
    void accionesUsanSemanticaAccesibleYTamanoIndependiente() {
        var nuevo = new AppActionButton(ActionType.NEW, ButtonSize.MAIN, "Nuevo usuario", null);
        assertTrue(nuevo.hasClassName("cc-action-new"));
        assertTrue(nuevo.hasClassName("cc-action-main"));
        assertEquals("Nuevo usuario", nuevo.getElement().getAttribute("aria-label"));

        var editar = new AppActionButton(ActionType.EDIT, ButtonSize.GRID_ACTION, null);
        assertTrue(editar.hasClassName("cc-action-edit"));
        assertTrue(editar.hasClassName("cc-action-grid"));
        assertEquals("Editar", editar.getElement().getAttribute("aria-label"));

        var guardar = new AppActionButton(ActionType.SAVE, ButtonSize.MAIN, null);
        assertTrue(guardar.hasClassName("cc-action-save"));
        assertEquals("Guardar", guardar.getElement().getAttribute("aria-label"));
        assertNotNull(guardar.getIcon());
    }

    @Test
    void paginacionIniciaEnDiezYNoPermiteTotalNegativo() {
        var changes = new ArrayList<AppPagination.PageRequest>();
        var pagination = new AppPagination(changes::add);
        assertEquals(new AppPagination.PageRequest(0, 10), pagination.currentRequest());
        pagination.setTotal(45);
        assertTrue(changes.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> pagination.setTotal(-1));
    }

    @Test
    void detallePresentaValoresSinInputsDeshabilitados() {
        var section = new AppDetailSection("Información general")
            .field("Nombre", "Empresa Demo")
            .field("RNC", null);
        assertEquals(2, section.getChildren().filter(child -> child.hasClassName("cc-detail-grid"))
            .findFirst().orElseThrow().getChildren().count());
    }
}
