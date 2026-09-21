package com.citacloud.springboot.contacloud.app.views.components;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;

import java.util.UUID;

public class CompanyLogoAvatar extends Div {
    private String initial;

    public CompanyLogoAvatar(String initial, boolean large) {
        this.initial = initial;
        addClassName("cc-company-avatar");
        if (large) addClassName("cc-company-avatar-large");
        getElement().setAttribute("aria-label", "Logo de la empresa");
    }

    public void refresh(boolean available) {
        removeAll();
        var fallback = new Span(initial);
        fallback.addClassName("cc-company-avatar-initial");
        if (available) {
            var image = new Image("/api/company/logo?v=" + UUID.randomUUID(), "Logo de la empresa");
            image.addClassName("cc-company-avatar-image");
            fallback.setVisible(false);
            image.getElement().addEventListener("error", event -> {
                image.setVisible(false);
                fallback.setVisible(true);
            });
            add(image);
        }
        add(fallback);
    }

    public void setInitial(String initial) {
        this.initial = initial == null || initial.isBlank() ? "C" : initial.substring(0, 1).toUpperCase();
    }
}
