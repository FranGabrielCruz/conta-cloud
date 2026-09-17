package com.citacloud.springboot.contacloud.app.views;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.*;
import jakarta.annotation.security.PermitAll;
@Route(value = "configuracion", layout = MainLayout.class) @PageTitle("Configuracion | ContaCloud") @PermitAll
public class PlaceholderView extends VerticalLayout {
    public PlaceholderView() { add(new H1("Configuracion"), new Paragraph("La infraestructura del modulo esta lista para continuar con su CRUD.")); }
}
