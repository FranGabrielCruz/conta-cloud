package com.citacloud.springboot.contacloud.app.views.components;

import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;

/** Acción principal del POS con contenido centrado y sin posicionamiento artificial. */
public final class PosChargeButton extends Button {
    public PosChargeButton(ComponentEventListener<ClickEvent<Button>> listener) {
        super(crearContenido(), listener);
        addClassName("cc-pos-charge");
        setTooltipText("Cobrar venta (F9 o doble Enter)");
        getElement().setAttribute("aria-label", "Cobrar venta, atajo F9 o doble Enter");
    }

    private static Div crearContenido() {
        var icono = VaadinIcon.CART.create();
        icono.addClassName("cc-pos-charge-icon");
        Div contenido = new Div(icono, new Span("COBRAR F9"));
        contenido.addClassName("cc-pos-charge-content");
        return contenido;
    }
}
