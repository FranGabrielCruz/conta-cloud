CREATE INDEX idx_database_node_filters ON database_node(type,status,code);
CREATE INDEX idx_tenant_migration_created ON tenant_migration(created_at DESC);
CREATE INDEX idx_tenant_migration_status ON tenant_migration(status,created_at DESC);

CREATE UNIQUE INDEX uk_tenant_migration_activa
    ON tenant_migration(tenant_id)
    WHERE status IN ('PENDIENTE','MIGRANDO','VERIFICANDO');
