package com.citacloud.springboot.contacloud.app.multitenancy;
import java.math.BigDecimal;
import java.util.UUID;
public record DatabaseNode(UUID id, String code, String name, DatabaseNodeType type, DatabaseNodeStatus status,
                           String hostReference, String databaseName, String region, int maxTenants,
                           int currentTenants, BigDecimal capacityThreshold, String schemaVersion,
                           String secretReference, boolean healthy) {
    public BigDecimal utilizationPercent() {
        return maxTenants <= 0 ? BigDecimal.valueOf(100) :
            BigDecimal.valueOf(currentTenants).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(maxTenants), 2, java.math.RoundingMode.HALF_UP);
    }
    public boolean acceptsNewTenant() {
        return status == DatabaseNodeStatus.ACTIVA && healthy && currentTenants < maxTenants
            && utilizationPercent().compareTo(capacityThreshold) < 0;
    }
}
