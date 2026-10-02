package com.citacloud.springboot.contacloud.app.mappers;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.CuentaBancaria;
import com.citacloud.springboot.contacloud.app.services.BankAccountNumberService;
import org.springframework.stereotype.Component;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class CuentaBancariaMapper {
    private final BankAccountNumberService numbers;

    public CuentaBancariaMapper(BankAccountNumberService numbers) { this.numbers = numbers; }

    public CuentaBancariaDto toDto(CuentaBancaria entity) {
        return new CuentaBancariaDto(entity.getId(), entity.getBancoNombre(), entity.getNombreCuenta(),
            entity.getTipoCuenta(), entity.getMonedaId(), entity.getMoneda().getCodigoIso(),
            entity.getMoneda().getNombre(), numbers.mask(entity.getNumeroCuentaUltimos4()),
            entity.getDescripcion(), entity.isActivo());
    }

    public CuentaBancariaDto toDto(CuentaBancaria entity,BigDecimal saldoApertura,
            LocalDate fechaSaldoApertura,BigDecimal saldoActual) {
        return new CuentaBancariaDto(entity.getId(),entity.getBancoNombre(),entity.getNombreCuenta(),
            entity.getTipoCuenta(),entity.getMonedaId(),entity.getMoneda().getCodigoIso(),entity.getMoneda().getNombre(),
            numbers.mask(entity.getNumeroCuentaUltimos4()),entity.getDescripcion(),entity.isActivo(),
            saldoApertura,fechaSaldoApertura,saldoActual);
    }

    public CuentaBancariaEdicionDto toEditDto(CuentaBancaria entity) {
        return new CuentaBancariaEdicionDto(entity.getId(), entity.getBancoNombre(),
            entity.getNombreCuenta(), entity.getTipoCuenta(), entity.getMonedaId(),
            numbers.decrypt(entity.getNumeroCuentaCifrado()), entity.getDescripcion(), entity.isActivo());
    }

    public CuentaBancaria toEntity(CuentaBancariaInput input, UUID tenantId, UUID empresaId,
                                    String codigo, String encrypted, String last4,
                                    String fingerprint, UUID usuarioId) {
        return new CuentaBancaria(tenantId, empresaId, input.monedaId(), codigo, input.banco(),
            input.nombre(), input.tipo(), encrypted, last4, fingerprint, input.descripcion(),
            input.activa(), usuarioId);
    }
}
