package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.MedioPagoMovimiento;
import com.citacloud.springboot.contacloud.app.models.TipoCuentaDinero;
import java.util.UUID;

public record PagoFacturaProveedorInput(
        TipoCuentaDinero tipoFuente,
        UUID fuenteId,
        MedioPagoMovimiento medioPago,
        String referencia,
        UUID claveIdempotencia) {
}
