package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.TipoCuentaDinero;
import java.util.UUID;
public record CuentaDineroOpcionDto(UUID id, TipoCuentaDinero tipo, String nombre,
                                    UUID monedaId, String monedaCodigo) {}
