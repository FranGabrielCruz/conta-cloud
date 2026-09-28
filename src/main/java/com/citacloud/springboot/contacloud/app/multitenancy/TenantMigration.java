package com.citacloud.springboot.contacloud.app.multitenancy;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TenantMigration(UUID id, UUID tenantId, String tenantNombre, String sourceCode,
                              String targetCode, String status, OffsetDateTime startedAt,
                              OffsetDateTime completedAt, OffsetDateTime createdAt) {}
