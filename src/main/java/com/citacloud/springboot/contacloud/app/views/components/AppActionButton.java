package com.citacloud.springboot.contacloud.app.views.components;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.icon.Icon;

import java.util.Objects;

/** Acción visual compartida; la autorización sigue siendo responsabilidad de cada View y del backend. */
public class AppActionButton extends Button {
    public AppActionButton(ActionType action, ButtonSize size, String label,
                           ComponentEventListener<ClickEvent<Button>> listener) {
        Objects.requireNonNull(action);
        Objects.requireNonNull(size);
        String accessibleLabel = Objects.requireNonNull(label).trim();
        if (accessibleLabel.isEmpty()) throw new IllegalArgumentException("La acción necesita una descripción");

        Icon icon = action.icon().create();
        icon.addClassName("cc-action-icon");
        icon.getElement().setAttribute("aria-hidden", "true");
        setIcon(icon);
        addClassNames("cc-action-button", "cc-action-" + action.color(),
            "cc-action-" + size.cssClass());
        getElement().setAttribute("aria-label", accessibleLabel);
        setTooltipText(accessibleLabel);
        if (listener != null) addClickListener(listener);
    }

    public AppActionButton(ActionType action, ButtonSize size,
                           ComponentEventListener<ClickEvent<Button>> listener) {
        this(action, size, action.defaultLabel(), listener);
    }
}
