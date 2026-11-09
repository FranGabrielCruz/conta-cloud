package com.citacloud.springboot.contacloud.app.views.components;

import com.vaadin.flow.component.html.Span;

/** Etiqueta compacta de estado con los colores semánticos del tema Lumo. */
public final class AppStatusBadge extends Span {
    public enum Variant {
        SUCCESS("success"), NEUTRAL("contrast"), DANGER("error"), WARNING("warning");

        private final String theme;

        Variant(String theme) {
            this.theme = theme;
        }
    }

    public AppStatusBadge(String label, Variant variant) {
        super(label);
        getElement().getThemeList().add("badge");
        getElement().getThemeList().add(variant.theme);
        getStyle().set("white-space", "nowrap");
    }

    public static AppStatusBadge activo(boolean activo) {
        return activo ? new AppStatusBadge("Activo", Variant.SUCCESS)
                      : new AppStatusBadge("Inactivo", Variant.NEUTRAL);
    }
}
