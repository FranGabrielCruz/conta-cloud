package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.multitenancy.DatabaseNodeStatus;
import com.citacloud.springboot.contacloud.app.multitenancy.DatabaseNodeType;
import java.math.BigDecimal;

public record DatabaseNodeInputDto(String codigo, String nombre, DatabaseNodeType tipo,
                                   DatabaseNodeStatus estado, String hostReference,
                                   String nombreBase, String region, int tenantsMaximos,
                                   BigDecimal umbralCapacidad, String versionSchema,
                                   boolean saludable) {}
