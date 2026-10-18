package com.citacloud.springboot.contacloud.app.views.components;

import com.citacloud.springboot.contacloud.app.dto.ClientePosDto;
import com.citacloud.springboot.contacloud.app.models.TipoIdentificacionCliente;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import java.util.Objects;

/** Línea compacta de un cliente ya existente asociado a la venta actual. */
public final class PosCustomerLine extends HorizontalLayout {

    public PosCustomerLine(ClientePosDto customer, Runnable changeAction, Runnable removeAction) {
        Objects.requireNonNull(customer, "El cliente es obligatorio.");
        Objects.requireNonNull(changeAction, "La acción cambiar es obligatoria.");
        Objects.requireNonNull(removeAction, "La acción quitar es obligatoria.");

        var icon = VaadinIcon.USER.create();
        icon.getElement().setAttribute("aria-hidden", "true");
        Span summary = new Span(summary(customer));
        summary.addClassName("cc-pos-customer-summary");
        summary.getElement().setAttribute("title", summary.getText());
        Div information = new Div(icon, summary);
        information.addClassName("cc-pos-customer-info");

        Button change = new Button("Cambiar", event -> changeAction.run());
        change.addClassName("cc-pos-customer-change");
        change.setTooltipText("Cambiar cliente");
        Button remove = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> removeAction.run());
        remove.addClassNames("cc-pos-icon-button", "cc-pos-customer-remove");
        remove.setTooltipText("Quitar cliente de la venta");
        remove.getElement().setAttribute("aria-label", "Quitar cliente de la venta");

        add(information, change, remove);
        addClassName("cc-pos-customer-line");
        setPadding(false);
        setSpacing(false);
        setWidthFull();
        setAlignItems(Alignment.CENTER);
        expand(information);
    }

    static String summary(ClientePosDto customer) {
        String name = clean(customer.nombre(), "Cliente");
        String identification = clean(customer.identificacion(), "");
        if (identification.isBlank()) return name;
        return name + " · " + identificationType(customer.tipoIdentificacion()) + " " + identification;
    }

    private static String identificationType(TipoIdentificacionCliente type) {
        if (type == null) return "ID";
        return switch (type) {
            case RNC -> "RNC";
            case CEDULA -> "Cédula";
            case PASAPORTE -> "Pasaporte";
            case IDENTIFICACION_EXTRANJERA -> "ID";
            case OTRO -> "Identificación";
        };
    }

    private static String clean(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
