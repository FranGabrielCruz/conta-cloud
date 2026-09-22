package com.citacloud.springboot.contacloud.app.multitenancy;
import java.util.UUID;
public record TenantRoute(UUID tenantId, TenantStatus tenantStatus, DatabaseNode databaseNode) {}
