package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.TipoCuentaBancaria;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CuentaBancariaInput(String banco, String nombre, TipoCuentaBancaria tipo,
                                  UUID monedaId, String numeroCuenta, String descripcion,
                                  boolean activa, BigDecimal saldoApertura, LocalDate fechaSaldoApertura) {
    public CuentaBancariaInput(String banco,String nombre,TipoCuentaBancaria tipo,UUID monedaId,
            String numeroCuenta,String descripcion,boolean activa){
        this(banco,nombre,tipo,monedaId,numeroCuenta,descripcion,activa,BigDecimal.ZERO,LocalDate.now());
    }
}
