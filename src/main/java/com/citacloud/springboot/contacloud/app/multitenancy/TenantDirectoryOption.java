package com.citacloud.springboot.contacloud.app.multitenancy;

import java.util.UUID;

public record TenantDirectoryOption(UUID tenantId, String nombre, DatabaseNode databaseNode) {}
