package com.citacloud.springboot.contacloud.app.views.components;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.server.VaadinService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;

import java.util.Objects;

/** Acción visual compartida; la autorización sigue siendo responsabilidad de cada View y del backend. */
public class AppActionButton extends Button {
    public static AppActionButton verPdf(ComponentEventListener<ClickEvent<Button>> listener) {
        return new AppActionButton(ActionType.PDF, ButtonSize.MAIN, listener);
    }

    public static AppActionButton verPdfEnlace() {
        var boton=verPdf(null);
        boton.getElement().executeJs("""
            this.addEventListener('click', () => {
                const url = this.getAttribute('data-pdf-url');
                if (url && !this.disabled) window.open(url, '_blank', 'noopener');
            });
            """);
        boton.setEnabled(false);
        return boton;
    }

    public void actualizarEnlacePdf(String ruta, Map<String,?> filtros) {
        if(ruta==null) {
            setEnabled(false);
            getElement().removeAttribute("data-pdf-url");
            return;
        }
        String contexto=VaadinService.getCurrentRequest().getContextPath();
        var query=new StringJoiner("&",contexto+ruta+"?","");
        filtros.forEach((nombre,valor)->query.add(nombre+"="+URLEncoder.encode(valor.toString(),StandardCharsets.UTF_8)));
        getElement().setAttribute("data-pdf-url",query.toString());
        setEnabled(true);
    }

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
