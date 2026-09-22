package com.citacloud.springboot.contacloud.app.dto;
import java.util.UUID;
public record ProvisioningResult(UUID tenantId,UUID empresaId,String empresaCodigo,String databaseNodeCode,String status){}
