package com.citacloud.springboot.contacloud.app.views;

import com.vaadin.flow.component.combobox.ComboBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConfiguracionViewTest {

    @Test
    void convierteTodosEnNullSinDesempaquetarBoolean() {
        var filter = new ComboBox<String>();
        filter.setItems("Todos", "Activas", "Inactivas");

        filter.setValue("Todos");
        assertNull(ConfiguracionView.statusValue(filter));

        filter.setValue("Activas");
        assertEquals(Boolean.TRUE, ConfiguracionView.statusValue(filter));

        filter.setValue("Inactivas");
        assertEquals(Boolean.FALSE, ConfiguracionView.statusValue(filter));
    }
}
