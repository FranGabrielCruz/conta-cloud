package com.citacloud.springboot.contacloud.app.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TenantMigrationDto(UUID id, UUID tenantId, String tenant, String origen,
                                 String destino, String estado, OffsetDateTime iniciada,
                                 OffsetDateTime finalizada, OffsetDateTime creada) {}
