CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE database_node (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('COMPARTIDA','DEDICADA')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVA','DRENANDO','LLENA','MANTENIMIENTO','INACTIVA')),
    host_reference VARCHAR(160) NOT NULL,
    database_name VARCHAR(100) NOT NULL UNIQUE,
    region VARCHAR(60),
    max_tenants INTEGER NOT NULL CHECK (max_tenants > 0),
    current_tenants INTEGER NOT NULL DEFAULT 0 CHECK (current_tenants >= 0),
    capacity_threshold NUMERIC(5,2) NOT NULL DEFAULT 90.00 CHECK (capacity_threshold > 0 AND capacity_threshold <= 100),
    storage_used BIGINT,
    storage_limit BIGINT,
    schema_version VARCHAR(50),
    secret_reference VARCHAR(160) NOT NULL,
    healthy BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE tenant_directory (
    tenant_id UUID PRIMARY KEY,
    database_node_id UUID NOT NULL REFERENCES database_node(id),
    hosting_type VARCHAR(20) NOT NULL CHECK (hosting_type IN ('COMPARTIDA','DEDICADA')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PROVISIONING','ACTIVE','SUSPENDED','MIGRATING','FAILED','CLOSED')),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    target_database_node_id UUID REFERENCES database_node(id),
    migration_status VARCHAR(30),
    version BIGINT NOT NULL DEFAULT 0,
    provisioning_key VARCHAR(120) UNIQUE
);
CREATE INDEX idx_tenant_directory_node_status ON tenant_directory(database_node_id,status);

CREATE TABLE company_directory (
    empresa_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant_directory(tenant_id),
    codigo VARCHAR(30) NOT NULL UNIQUE,
    nombre VARCHAR(150) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_company_directory_tenant ON company_directory(tenant_id);

CREATE TABLE tenant_migration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant_directory(tenant_id),
    source_database_node_id UUID NOT NULL REFERENCES database_node(id),
    target_database_node_id UUID NOT NULL REFERENCES database_node(id),
    status VARCHAR(30) NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    error_message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

