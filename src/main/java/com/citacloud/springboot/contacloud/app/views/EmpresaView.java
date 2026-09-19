package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.EmpresaDto;
import com.citacloud.springboot.contacloud.app.services.EmpresaService;
import com.citacloud.springboot.contacloud.app.views.components.AppDetailSection;
import com.citacloud.springboot.contacloud.app.views.components.AppPageHeader;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;

@Route(value = "empresa", layout = MainLayout.class) @PageTitle("Empresa | ContaCloud") @PermitAll
public class EmpresaView extends VerticalLayout {
    public EmpresaView(EmpresaService service) {
        EmpresaDto empresa = service.obtenerActual();
        addClassName("cc-page");
        setPadding(false);
        setSpacing(false);
        setWidthFull();
        var details = new AppDetailSection("Información general")
            .field("Nombre", empresa.nombre())
            .field("Identificación fiscal", empresa.identificacionFiscal())
            .field("País", empresa.paisCodigo())
            .field("Zona horaria", empresa.zonaHoraria())
            .field("Código", empresa.codigo())
            .field("Estado", empresa.activo() ? "Activa" : "Inactiva");
        add(new AppPageHeader("Datos de empresa", "Información general de la empresa activa."), details);
    }
}
