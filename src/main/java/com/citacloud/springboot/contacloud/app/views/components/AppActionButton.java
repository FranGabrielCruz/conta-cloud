package com.citacloud.springboot.contacloud.app.views.components;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.icon.Icon;

import java.util.Objects;

/** Acción visual compartida; la autorización sigue siendo responsabilidad de cada View y del backend. */
public class AppActionButton extends Button {
    private static final String SAVE_SVG = """
        <span class="cc-action-icon" aria-hidden="true"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor"
             stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2Z"/>
          <path d="M17 21v-8H7v8M7 3v5h8"/>
        </svg></span>
        """;

    public AppActionButton(ActionType action, ButtonSize size, String label,
                           ComponentEventListener<ClickEvent<Button>> listener) {
        Objects.requireNonNull(action);
        Objects.requireNonNull(size);
        String accessibleLabel = Objects.requireNonNull(label).trim();
        if (accessibleLabel.isEmpty()) throw new IllegalArgumentException("La acción necesita una descripción");

        if (action == ActionType.SAVE) {
            setIcon(new Html(SAVE_SVG));
        } else {
            Icon icon = action.icon().create();
            icon.addClassName("cc-action-icon");
            setIcon(icon);
        }
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
