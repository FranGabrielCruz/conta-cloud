package com.citacloud.springboot.contacloud.app.views.components;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class VerPdfActionTest {
    @Test
    void usaElDocumentoAzulSinTextoYConEtiquetaAccesible() {
        var ejecutado = new AtomicBoolean();
        var boton = AppActionButton.verPdf(e -> ejecutado.set(true));

        assertThat(boton.getClassNames()).contains("cc-action-main", "cc-action-view");
        assertThat(boton.getIcon().getElement().getAttribute("icon")).isEqualTo("vaadin:file-text-o");
        assertThat(boton.getIcon().getElement().getAttribute("aria-hidden")).isEqualTo("true");
        assertThat(boton.getAriaLabel()).contains("Ver PDF");
        assertThat(boton.getTooltip().getText()).isEqualTo("Ver PDF");
        assertThat(boton.getText()).isEmpty();
        boton.click();
        assertThat(ejecutado).isTrue();
    }
}
