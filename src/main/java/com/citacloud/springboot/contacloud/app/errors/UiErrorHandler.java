package com.citacloud.springboot.contacloud.app.errors;

import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UiErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(UiErrorHandler.class);

    private UiErrorHandler() {}

    public static String message(Throwable error, String fallback) {
        ClassifiedError classified = ErrorClassifier.classify(error);
        if (classified.expected()) return classified.message();
        String traceId = TraceContext.currentOrCreate();
        logUnexpected(error, classified, traceId, fallback);
        String base = fallback == null || fallback.isBlank() ? classified.message() : fallback;
        return base + " Código de seguimiento: " + TraceContext.shortId(traceId);
    }

    public static void show(Throwable error, String fallback) {
        Notification notification = Notification.show(message(error, fallback), 6000, Notification.Position.BOTTOM_START);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    private static void logUnexpected(Throwable error, ClassifiedError classified, String traceId, String operation) {
        String tenant = "anonymous", company = "anonymous", user = "anonymous";
        try {
            var principal = TenantContext.principalActual();
            tenant = principal.tenantId().toString();
            company = principal.empresaId().toString();
            user = principal.usuarioId().toString();
        } catch (RuntimeException ignored) {
            // No hay identidad autenticada; no se inventa contexto de tenant.
        }
        log.error("event=UI_OPERATION_FAILED traceId={} tenantId={} companyId={} userId={} operation={} code={} result=ERROR exceptionType={}",
            traceId, tenant, company, user, operation, classified.code(), error.getClass().getName(), error);
    }
}
