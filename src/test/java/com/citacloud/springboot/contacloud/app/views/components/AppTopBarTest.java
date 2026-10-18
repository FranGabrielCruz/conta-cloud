package com.citacloud.springboot.contacloud.app.views.components;

import com.citacloud.springboot.contacloud.app.dto.TerminalPosDto;
import com.citacloud.springboot.contacloud.app.services.ContextoUsuarioService;
import com.vaadin.flow.component.Component;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class AppTopBarTest {

    @Test
    void muestraModoNormalSinInformacionOperativa() {
        AppTopBar topBar = topBar();

        assertFalse(topBar.isPosMode());
        assertTrue(buscar(topBar, "cc-context-badge").isVisible());
        assertFalse(buscar(topBar, "cc-header-pos-title").isVisible());
        assertFalse(buscar(topBar, "cc-header-pos-cash-host").isVisible());
        assertEquals(1, contar(topBar, "cc-user-button"));
    }

    @Test
    void integraContextoPosEnLaMismaBarraGlobalSinDuplicarUsuario() {
        AppTopBar topBar = topBar();
        TerminalPosDto terminal = terminal(true, "Caja 01", "Sucursal Principal",
                LocalDate.of(2026, 10, 17));

        topBar.showPosMode(terminal, "admin", action -> { });

        assertTrue(topBar.isPosMode());
        assertFalse(buscar(topBar, "cc-context-badge").isVisible());
        assertTrue(buscar(topBar, "cc-header-pos-title").isVisible());
        assertEquals("PUNTO DE VENTA", buscar(topBar, "cc-header-pos-title").getElement().getText());
        assertEquals("Caja 01 · Sucursal Principal, abierta. Operaciones de caja",
                buscar(topBar, "cc-header-pos-cash").getElement().getAttribute("aria-label"));
        assertEquals("Cajero admin",
                buscar(topBar, "cc-user-button").getElement().getAttribute("aria-label"));
        assertEquals("Fecha operativa 17 de octubre de 2026",
                buscar(topBar, "cc-header-pos-date").getElement().getAttribute("aria-label"));
        assertEquals(1, contar(topBar, "cc-user-button"));
    }

    @Test
    void vuelveAlModoNormalAlAbandonarElPos() {
        AppTopBar topBar = topBar();
        topBar.showPosMode(terminal(true, "Caja 01", "Principal", LocalDate.now()),
                "admin", action -> { });

        topBar.showDefaultMode();

        assertFalse(topBar.isPosMode());
        assertTrue(buscar(topBar, "cc-context-badge").isVisible());
        assertFalse(buscar(topBar, "cc-header-pos-title").isVisible());
        assertEquals("Abrir menú de usuario",
                buscar(topBar, "cc-user-button").getElement().getAttribute("aria-label"));
    }

    @Test
    void resuelveCajaYFechaOperativa() {
        TerminalPosDto terminal = terminal(true, "Caja 01", "Principal", LocalDate.of(2026, 10, 17));

        assertEquals("Caja 01 · Principal", AppTopBar.cashLabel(terminal));
        assertEquals("17/10/2026", AppTopBar.formatDate(terminal.fechaOperativa()));
    }

    @Test
    void noActivaModoPosConCajaCerrada() {
        AppTopBar topBar = topBar();
        TerminalPosDto terminal = terminal(false, "Caja 01", "Principal", null);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> topBar.showPosMode(terminal, "admin", action -> { }));

        assertEquals("El modo POS solo puede mostrarse con una caja abierta.", error.getMessage());
        assertFalse(topBar.isPosMode());
    }

    private static AppTopBar topBar() {
        return new AppTopBar(new ContextoUsuarioService.Contexto("admin", "DEMO", "DEMO", "D"));
    }

    private static TerminalPosDto terminal(boolean open, String cash, String branch, LocalDate date) {
        return new TerminalPosDto(UUID.randomUUID(), "Terminal principal", UUID.randomUUID(), branch,
                UUID.randomUUID(), cash, "DOP", open, open ? UUID.randomUUID() : null, date);
    }

    private static Component buscar(Component root, String className) {
        return descendants(root)
                .filter(component -> component.getClassNames().contains(className))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No se encontró " + className));
    }

    private static long contar(Component root, String className) {
        return descendants(root).filter(component -> component.getClassNames().contains(className)).count();
    }

    private static Stream<Component> descendants(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(AppTopBarTest::descendants));
    }
}
