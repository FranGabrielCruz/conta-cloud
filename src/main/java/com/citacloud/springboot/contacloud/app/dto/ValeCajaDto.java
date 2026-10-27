package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.EstadoValeCaja;import java.math.BigDecimal;import java.time.OffsetDateTime;import java.util.UUID;
public record ValeCajaDto(UUID id,String numero,OffsetDateTime fechaHora,String caja,String sucursal,String monedaCodigo,String usuario,
 String concepto,String beneficiario,BigDecimal importe,String referencia,String observacion,EstadoValeCaja estado,UUID sesionId,
 UUID movimientoId,OffsetDateTime anuladoEn,String usuarioAnulacion,String motivoAnulacion,UUID movimientoReversionId,boolean anulable,
 String empresaNombre,String empresaIdentificacion,String empresaDireccion,String empresaTelefono,String empresaCorreo,String empresaLogoObjectKey){}
