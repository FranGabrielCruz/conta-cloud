package com.citacloud.springboot.contacloud.app.multitenancy;
import java.util.*;
public interface DirectoryGateway {
    Optional<CompanyLocator> findCompanyByCode(String code);
    Optional<TenantRoute> findTenantRoute(UUID tenantId);
    Optional<ProvisioningLookup> findProvisioningByKey(String idempotencyKey);
    Optional<DatabaseNode> findNode(UUID nodeId);
    DirectoryPage<DatabaseNode> searchNodes(String query, DatabaseNodeType type, DatabaseNodeStatus status,
                                            int page, int size, boolean searchDatabaseName);
    DatabaseNode createNode(DatabaseNode node);
    DatabaseNode updateNode(DatabaseNode node);
    List<DatabaseNode> findEligibleSharedNodes(String requiredSchemaVersion);
    List<DatabaseNode> findEligibleDedicatedNodes(String requiredSchemaVersion);
    DatabaseNode reserveAutomatic(String requiredSchemaVersion);
    DatabaseNode reserveManual(UUID nodeId, DatabaseNodeType requiredType, String requiredSchemaVersion);
    void releaseReservation(UUID nodeId);
    void createProvisioningTenant(UUID tenantId, UUID nodeId, DatabaseNodeType hostingType, String idempotencyKey);
    void markTenantStatus(UUID tenantId, TenantStatus status);
    void registerCompany(UUID empresaId, UUID tenantId, String codigo, String nombre);
    void setCompanyActive(UUID empresaId, boolean active);
    List<TenantDirectoryOption> searchTenants(String query, int limit);
    List<DatabaseNode> findEligibleMigrationTargets(UUID tenantId, String requiredSchemaVersion);
    DirectoryPage<TenantMigration> searchMigrations(String query, String status, int page, int size);
    TenantMigration findMigration(UUID migrationId);
    TenantMigration scheduleMigration(UUID tenantId, UUID targetNodeId, String requiredSchemaVersion);
}
