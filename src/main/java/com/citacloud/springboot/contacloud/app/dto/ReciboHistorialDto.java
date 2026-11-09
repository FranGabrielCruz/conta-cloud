package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.EstadoReciboCliente;
import com.citacloud.springboot.contacloud.app.reports.pos.PaymentReceiptPrintDocument;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ReciboHistorialDto(CobroClienteDto recibo, PaymentReceiptPrintDocument documento,
        LocalDateTime anuladoEn, String anuladoPor, boolean anulable) {
    public record Resumen(UUID id, String numero, LocalDateTime fecha, String cliente, String moneda,
            BigDecimal total, EstadoReciboCliente estado, List<String> documentos) {}
}
