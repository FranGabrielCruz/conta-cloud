package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.dto.EmpresaDto;
import com.citacloud.springboot.contacloud.app.services.EmpresaService;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;

@Route(value = "empresa", layout = MainLayout.class) @PageTitle("Empresa | ContaCloud") @PermitAll
public class EmpresaView extends VerticalLayout {
    public EmpresaView(EmpresaService service) {
        EmpresaDto empresa = service.obtenerActual();
        var codigo = new TextField("Codigo", empresa.codigo(), ""); codigo.setReadOnly(true);
        var nombre = new TextField("Nombre", empresa.nombre(), ""); nombre.setReadOnly(true);
        var fiscal = new TextField("Identificacion fiscal", empresa.identificacionFiscal(), ""); fiscal.setReadOnly(true);
        setMaxWidth("800px"); add(new H1("Datos de empresa"), codigo, nombre, fiscal);
    }
}
