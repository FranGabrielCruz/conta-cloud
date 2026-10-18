package com.citacloud.springboot.contacloud.app.views.components;

import com.citacloud.springboot.contacloud.app.dto.ClientePosDto;
import com.citacloud.springboot.contacloud.app.models.TipoIdentificacionCliente;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class PosCustomerLineTest {

    @Test
    void presentaNombreEIdentificacionEnUnaSolaLinea() {
        ClientePosDto customer = new ClientePosDto(UUID.randomUUID(), "CLI-001", "Ferretería Central, SRL",
                TipoIdentificacionCliente.RNC, "131-12345-6", "809-555-0101");

        PosCustomerLine line = new PosCustomerLine(customer, () -> { }, () -> { });

        assertEquals("Ferretería Central, SRL · RNC 131-12345-6", PosCustomerLine.summary(customer));
        assertTrue(line.getClassNames().contains("cc-pos-customer-line"));
    }

    @Test
    void omiteIdentificacionCuandoElClienteNoLaTiene() {
        ClientePosDto customer = new ClientePosDto(UUID.randomUUID(), "CLI-002", "Cliente extranjero",
                null, null, null);

        assertEquals("Cliente extranjero", PosCustomerLine.summary(customer));
    }

    @Test
    void ofreceSoloCambiarYQuitarElClienteActual() {
        AtomicBoolean changed = new AtomicBoolean();
        AtomicBoolean removed = new AtomicBoolean();
        ClientePosDto customer = new ClientePosDto(UUID.randomUUID(), "CLI-003", "Juan Pérez",
                TipoIdentificacionCliente.CEDULA, "001-1234567-8", null);
        PosCustomerLine line = new PosCustomerLine(customer, () -> changed.set(true), () -> removed.set(true));

        assertEquals("Juan Pérez · Cédula 001-1234567-8", PosCustomerLine.summary(customer));
        assertEquals(2, line.getChildren().filter(component -> component instanceof com.vaadin.flow.component.button.Button).count());
        assertFalse(line.getElement().getText().contains("Nuevo"));
        assertFalse(changed.get());
        assertFalse(removed.get());
    }
}
