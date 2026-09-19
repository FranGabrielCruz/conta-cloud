package com.citacloud.springboot.contacloud.app.views.components;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;

/** Datos de solo lectura como texto, no como formulario deshabilitado. */
public class AppDetailSection extends Div {
    private final Div fields = new Div();

    public AppDetailSection(String title) {
        addClassNames("cc-card", "cc-detail-section");
        var heading = new H2(title);
        heading.addClassName("cc-section-heading");
        fields.addClassName("cc-detail-grid");
        add(heading, fields);
    }

    public AppDetailSection field(String label, String value) {
        var item = new Div();
        item.addClassName("cc-detail-field");
        var fieldLabel = new Span(label);
        fieldLabel.addClassName("cc-detail-label");
        var fieldValue = new Span(value == null || value.isBlank() ? "No especificado" : value);
        fieldValue.addClassName("cc-detail-value");
        item.add(fieldLabel, fieldValue);
        fields.add(item);
        return this;
    }
}
