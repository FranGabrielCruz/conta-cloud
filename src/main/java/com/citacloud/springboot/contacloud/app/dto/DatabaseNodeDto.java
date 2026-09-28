package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.multitenancy.DatabaseNodeStatus;
import com.citacloud.springboot.contacloud.app.multitenancy.DatabaseNodeType;
import java.math.BigDecimal;
import java.util.UUID;

public record DatabaseNodeDto(UUID id, String codigo, String nombre, DatabaseNodeType tipo,
                              int tenantsActuales, int tenantsMaximos, BigDecimal capacidad,
                              BigDecimal umbralCapacidad,
                              DatabaseNodeStatus estado, String versionSchema, boolean saludable,
                              String nombreBase, String region) {}
