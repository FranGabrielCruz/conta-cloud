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
    RETURN("Iniciar devolución", VaadinIcon.REPLY, "neutral"),
    DELETE("Eliminar", VaadinIcon.TRASH, "danger"),
    ACTIVATE("Activar", VaadinIcon.POWER_OFF, "success"),
    DEACTIVATE("Desactivar", VaadinIcon.POWER_OFF, "danger"),
    VOID("Anular", VaadinIcon.BAN, "danger"),
    REVERSE("Revertir", VaadinIcon.ROTATE_LEFT, "danger"),
    ISSUE("Emitir", VaadinIcon.PAPERPLANE, "success"),
    PRINT("Imprimir", VaadinIcon.PRINT, "view"),
    REFRESH("Actualizar", VaadinIcon.REFRESH, "neutral"),
    DUPLICATE("Duplicar", VaadinIcon.COPY, "neutral"),
    RECOVER("Recuperar", VaadinIcon.PLAY, "view"),
    ACCEPT("Aceptar", VaadinIcon.CHECK, "success"),
    REJECT("Rechazar", VaadinIcon.CLOSE, "danger"),
    CREATE_INVOICE("Crear factura", VaadinIcon.FILE_TEXT_O, "success"),
    MATCH("Conciliar", VaadinIcon.CONNECT, "success"),
    LINK("Generar código de vinculación", VaadinIcon.LINK, "new"),
    UNMATCH("Desconciliar", VaadinIcon.UNLINK, "danger"),
    FINALIZE("Finalizar", VaadinIcon.LOCK, "danger"),
    OPEN_CASH("Abrir caja", VaadinIcon.UNLOCK, "success"),
    CLOSE_CASH("Cerrar caja", VaadinIcon.LOCK, "danger"),
    COLLECT("Cobrar", VaadinIcon.MONEY, "success"),
    REVIEW("Revisar", VaadinIcon.CHECK, "success"),
    COUNT("Realizar conteo", VaadinIcon.CHECK_SQUARE_O, "success"),
    APPLY_CREDIT("Aplicar crédito", VaadinIcon.CHECK_CIRCLE, "success"),
    CLOSE_PERIOD("Cerrar período", VaadinIcon.LOCK, "danger"),
    REOPEN_PERIOD("Reabrir período", VaadinIcon.UNLOCK, "success");

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
