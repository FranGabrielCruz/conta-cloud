package com.citacloud.springboot.contacloud.app.views.components;

import org.junit.jupiter.api.Test;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;

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
        assertTrue(guardar.getIcon().hasClassName("cc-action-icon"));
        assertEquals("vaadin-icon", guardar.getIcon().getElement().getTag());
    }

    @Test
    void tablaEstandarAutoajustaAlturaSinAlterarPaginacionBackend() {
        var grid = new AppGrid<String>(String.class);
        assertTrue(grid.isAllRowsVisible());
        assertTrue(grid.hasClassName("cc-auto-grid"));
        assertEquals("100%", grid.getWidth());
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

    @Test
    void logoUsaFallbackYLaMismaUrlProtegidaEnAmbosTamanos() {
        var menuAvatar = new CompanyLogoAvatar("C", false);
        menuAvatar.refresh(false);
        assertTrue(menuAvatar.hasClassName("cc-company-avatar"));
        assertFalse(menuAvatar.hasClassName("cc-company-avatar-large"));
        assertEquals("C", menuAvatar.getChildren().map(Span.class::cast).findFirst().orElseThrow().getText());

        var preview = new CompanyLogoAvatar("C", true);
        preview.refresh(true);
        assertTrue(preview.hasClassName("cc-company-avatar-large"));
        var image = preview.getChildren().filter(Image.class::isInstance).map(Image.class::cast)
            .findFirst().orElseThrow();
        assertTrue(image.getSrc().startsWith("/api/company/logo?v="));
    }
}
