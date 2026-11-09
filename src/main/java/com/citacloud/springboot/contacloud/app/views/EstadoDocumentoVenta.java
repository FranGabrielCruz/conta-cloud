package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.models.EstadoReciboCliente;
import com.citacloud.springboot.contacloud.app.views.components.AppStatusBadge;
import com.citacloud.springboot.contacloud.app.views.components.AppStatusBadge.Variant;

enum EstadoDocumentoVenta {
    EMITIDO("Emitido", Variant.SUCCESS),
    ANULADO("Anulado", Variant.DANGER),
    DESCONOCIDO("Desconocido", Variant.NEUTRAL);

    private final String texto;
    private final Variant variante;

    EstadoDocumentoVenta(String texto, Variant variante) {
        this.texto = texto;
        this.variante = variante;
    }

    AppStatusBadge badge() {
        return new AppStatusBadge(texto, variante);
    }

    static AppStatusBadge nota(String estado) {
        return switch (estado) {
            case "CONFIRMED" -> EMITIDO.badge();
            case "VOIDED" -> ANULADO.badge();
            case null, default -> DESCONOCIDO.badge();
        };
    }

    static AppStatusBadge recibo(EstadoReciboCliente estado) {
        return switch (estado) {
            case AVAILABLE, PARTIALLY_APPLIED, APPLIED -> EMITIDO.badge();
            case VOIDED -> ANULADO.badge();
            case null -> DESCONOCIDO.badge();
        };
    }
}
