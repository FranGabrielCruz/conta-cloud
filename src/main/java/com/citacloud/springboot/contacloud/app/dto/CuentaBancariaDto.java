package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.TipoCuentaBancaria;
import java.util.UUID;

public record CuentaBancariaDto(UUID id, String banco, String nombre, TipoCuentaBancaria tipo,
                                UUID monedaId, String monedaCodigo, String monedaNombre,
                                String numeroEnmascarado, String descripcion, boolean activa) {}
