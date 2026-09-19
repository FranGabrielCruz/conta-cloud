package com.citacloud.springboot.contacloud.app.views.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;

/** Título, descripción y acción primaria en una posición uniforme. */
public class AppPageHeader extends Div {
    public AppPageHeader(String title, String description, Component... actions) {
        addClassName("cc-page-header");
        var copy = new Div();
        copy.addClassName("cc-page-header-copy");
        var heading = new H1(title);
        heading.addClassName("cc-page-title");
        copy.add(heading);
        if (description != null && !description.isBlank()) {
            var subtitle = new Paragraph(description);
            subtitle.addClassName("cc-subtitle");
            copy.add(subtitle);
        }
        add(copy);
        if (actions.length > 0) {
            var actionArea = new Div(actions);
            actionArea.addClassName("cc-page-header-actions");
            add(actionArea);
        }
    }
}
