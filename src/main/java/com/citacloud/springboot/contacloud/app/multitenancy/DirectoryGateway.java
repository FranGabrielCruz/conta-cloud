package com.citacloud.springboot.contacloud.app.multitenancy;
import java.util.*;
public interface DirectoryGateway {
    Optional<CompanyLocator> findCompanyByCode(String code);
    Optional<TenantRoute> findTenantRoute(UUID tenantId);
    Optional<ProvisioningLookup> findProvisioningByKey(String idempotencyKey);
    Optional<DatabaseNode> findNode(UUID nodeId);
    List<DatabaseNode> findEligibleSharedNodes(String requiredSchemaVersion);
    List<DatabaseNode> findEligibleDedicatedNodes(String requiredSchemaVersion);
    DatabaseNode reserveAutomatic(String requiredSchemaVersion);
    DatabaseNode reserveManual(UUID nodeId, DatabaseNodeType requiredType, String requiredSchemaVersion);
    void releaseReservation(UUID nodeId);
    void createProvisioningTenant(UUID tenantId, UUID nodeId, DatabaseNodeType hostingType, String idempotencyKey);
    void markTenantStatus(UUID tenantId, TenantStatus status);
    void registerCompany(UUID empresaId, UUID tenantId, String codigo, String nombre);
    void setCompanyActive(UUID empresaId, boolean active);
}
