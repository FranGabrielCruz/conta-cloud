package com.citacloud.springboot.contacloud.app.views.components;

import com.citacloud.springboot.contacloud.app.dto.ArchivoPdfDto;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.server.VaadinSession;
import java.io.ByteArrayInputStream;

public final class PdfEnPestana {
    private PdfEnPestana() {}

    public static void abrir(ArchivoPdfDto archivo) {
        var recurso = new StreamResource(archivo.nombreArchivo(), () -> new ByteArrayInputStream(archivo.contenido()));
        recurso.setContentType("application/pdf");
        recurso.setCacheTime(0);
        var registro = VaadinSession.getCurrent().getResourceRegistry().registerResource(recurso);
        UI.getCurrent().getPage().open(registro.getResourceUri().toString(), "_blank");
    }
}
