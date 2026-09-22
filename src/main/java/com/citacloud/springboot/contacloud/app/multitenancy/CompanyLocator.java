package com.citacloud.springboot.contacloud.app.multitenancy;
import java.util.UUID;
public record CompanyLocator(UUID empresaId, UUID tenantId, String codigo, boolean activo) {}
