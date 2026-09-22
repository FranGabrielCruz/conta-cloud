package com.citacloud.springboot.contacloud.app.multitenancy;

import java.util.UUID;

public record ProvisioningLookup(UUID tenantId, TenantStatus status, UUID empresaId,
                                 String empresaCodigo, String databaseNodeCode) {
    public boolean completed() {
        return status == TenantStatus.ACTIVE && empresaId != null && empresaCodigo != null;
    }
}
