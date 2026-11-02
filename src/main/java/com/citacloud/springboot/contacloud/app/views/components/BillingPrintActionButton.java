package com.citacloud.springboot.contacloud.app.views.components;

import java.util.UUID;
import java.util.function.BiFunction;
import com.citacloud.springboot.contacloud.app.errors.UiErrorHandler;

/** Keeps the same intent until acknowledged; each subsequent action gets a new intent. */
public class BillingPrintActionButton extends AppActionButton {
    private UUID attempt = UUID.randomUUID();
    public BillingPrintActionButton(UUID document, ButtonSize size, String label,
                                    BiFunction<UUID, UUID, Boolean> command) {
        super(ActionType.PRINT, size, label, null);
        setDisableOnClick(true);
        addClickListener(event -> {
            try {
                if (Boolean.TRUE.equals(command.apply(document, attempt))) attempt = UUID.randomUUID();
            } catch (RuntimeException ex) {
                UiErrorHandler.show(ex, "La operación permanece guardada. No fue posible solicitar su impresión. Puede reintentar.");
            } finally {
                setEnabled(true);
            }
        });
    }
}
