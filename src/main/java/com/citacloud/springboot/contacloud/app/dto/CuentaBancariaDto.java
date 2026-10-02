package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.TipoCuentaBancaria;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CuentaBancariaDto(UUID id, String banco, String nombre, TipoCuentaBancaria tipo,
                                UUID monedaId, String monedaCodigo, String monedaNombre,
                                String numeroEnmascarado, String descripcion, boolean activa,
                                BigDecimal saldoApertura, LocalDate fechaSaldoApertura, BigDecimal saldoActual) {
    public CuentaBancariaDto(UUID id,String banco,String nombre,TipoCuentaBancaria tipo,UUID monedaId,
            String monedaCodigo,String monedaNombre,String numeroEnmascarado,String descripcion,boolean activa){
        this(id,banco,nombre,tipo,monedaId,monedaCodigo,monedaNombre,numeroEnmascarado,descripcion,activa,null,null,null);
    }
}
