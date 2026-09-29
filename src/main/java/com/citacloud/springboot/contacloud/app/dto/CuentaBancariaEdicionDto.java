package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.TipoCuentaBancaria;
import java.util.UUID;

public record CuentaBancariaEdicionDto(UUID id, String banco, String nombre,
                                       TipoCuentaBancaria tipo, UUID monedaId,
                                       String numeroCuenta, String descripcion,
                                       boolean activa) {}
