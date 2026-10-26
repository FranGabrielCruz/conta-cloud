package com.citacloud.springboot.contacloud.app.repositories;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public interface MovimientoCajaProjection {
    UUID getId(); String getNumero(); Instant getFechaHora(); String getCaja(); String getSucursal();
    String getUsuario(); String getTipo(); String getOrigen(); String getConcepto(); String getReferencia();
    BigDecimal getImporte(); BigDecimal getSaldo(); String getMonedaCodigo(); String getObservacion();
    String getEstado(); UUID getReversionDeId(); boolean isReversible();
}
