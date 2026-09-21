package com.citacloud.springboot.contacloud.app.views.components;

import com.vaadin.flow.component.icon.VaadinIcon;

public enum ActionType {
    NEW("Nuevo registro", VaadinIcon.PLUS, "new"),
    SAVE("Guardar", VaadinIcon.DISC, "save"),
    EDIT("Editar", VaadinIcon.PENCIL, "edit"),
    VIEW("Ver", VaadinIcon.EYE, "view"),
    CANCEL("Cancelar", VaadinIcon.CLOSE, "neutral"),
    CLOSE("Cerrar", VaadinIcon.CLOSE, "neutral"),
    BACK("Volver", VaadinIcon.ARROW_LEFT, "neutral"),
    DELETE("Eliminar", VaadinIcon.TRASH, "danger"),
    ACTIVATE("Activar", VaadinIcon.POWER_OFF, "success"),
    DEACTIVATE("Desactivar", VaadinIcon.POWER_OFF, "danger");

    private final String defaultLabel;
    private final VaadinIcon icon;
    private final String color;

    ActionType(String defaultLabel, VaadinIcon icon, String color) {
        this.defaultLabel = defaultLabel;
        this.icon = icon;
        this.color = color;
    }

    public String defaultLabel() { return defaultLabel; }
    public VaadinIcon icon() { return icon; }
    public String color() { return color; }
}
