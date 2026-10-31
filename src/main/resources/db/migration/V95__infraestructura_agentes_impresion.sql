CREATE TABLE print_agent_sequence (
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    current_value BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, empresa_id),
    CONSTRAINT fk_print_agent_sequence_empresa
        FOREIGN KEY (tenant_id, empresa_id) REFERENCES empresas(tenant_id, id)
);

CREATE TABLE print_agents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    sucursal_id UUID NOT NULL,
    display_code VARCHAR(30) NOT NULL,
    name VARCHAR(150) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    machine_name VARCHAR(150),
    application_version VARCHAR(60),
    operating_system VARCHAR(150),
    last_seen_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revoked_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_print_agent_empresa
        FOREIGN KEY (tenant_id, empresa_id) REFERENCES empresas(tenant_id, id),
    CONSTRAINT fk_print_agent_sucursal
        FOREIGN KEY (empresa_id, sucursal_id) REFERENCES sucursales(empresa_id, id),
    CONSTRAINT fk_print_agent_created_by FOREIGN KEY (created_by) REFERENCES usuarios(id),
    CONSTRAINT fk_print_agent_revoked_by FOREIGN KEY (revoked_by) REFERENCES usuarios(id),
    CONSTRAINT ck_print_agent_status CHECK (status IN ('PENDING','ACTIVE','REVOKED')),
    CONSTRAINT uk_print_agent_display UNIQUE (tenant_id, empresa_id, display_code),
    CONSTRAINT uk_print_agent_tenant_id UNIQUE (tenant_id, empresa_id, id)
);
CREATE INDEX idx_print_agent_list ON print_agents(tenant_id, empresa_id, status, created_at DESC, id DESC);
CREATE INDEX idx_print_agent_branch ON print_agents(tenant_id, empresa_id, sucursal_id);

CREATE TABLE print_agent_pairings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    agent_id UUID NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    invalidated_at TIMESTAMPTZ,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_print_agent_pairing_agent
        FOREIGN KEY (tenant_id, empresa_id, agent_id) REFERENCES print_agents(tenant_id, empresa_id, id),
    CONSTRAINT fk_print_agent_pairing_created_by FOREIGN KEY (created_by) REFERENCES usuarios(id),
    CONSTRAINT ck_print_agent_pairing_attempts CHECK (failed_attempts >= 0),
    CONSTRAINT uk_print_agent_pairing_hash UNIQUE (code_hash)
);
CREATE INDEX idx_print_agent_pairing_agent ON print_agent_pairings(tenant_id, empresa_id, agent_id, created_at DESC);
CREATE UNIQUE INDEX uk_print_agent_pairing_active ON print_agent_pairings(agent_id)
    WHERE consumed_at IS NULL AND invalidated_at IS NULL;

CREATE TABLE print_agent_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    agent_id UUID NOT NULL,
    access_token_hash VARCHAR(64) NOT NULL,
    access_expires_at TIMESTAMPTZ NOT NULL,
    refresh_token_hash VARCHAR(64) NOT NULL,
    refresh_expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_authenticated_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    rotated_from_id UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_print_agent_credential_agent
        FOREIGN KEY (tenant_id, empresa_id, agent_id) REFERENCES print_agents(tenant_id, empresa_id, id),
    CONSTRAINT fk_print_agent_credential_rotated
        FOREIGN KEY (rotated_from_id) REFERENCES print_agent_credentials(id),
    CONSTRAINT uk_print_agent_access_hash UNIQUE (access_token_hash),
    CONSTRAINT uk_print_agent_refresh_hash UNIQUE (refresh_token_hash)
);
CREATE INDEX idx_print_agent_credential_agent ON print_agent_credentials(tenant_id, empresa_id, agent_id, created_at DESC);
CREATE UNIQUE INDEX uk_print_agent_credential_active ON print_agent_credentials(agent_id)
    WHERE revoked_at IS NULL;

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('agentes_impresion.ver','Ver agentes de impresión','Consultar agentes autorizados para contaCloudPrint','CONFIGURACION','agentes_impresion','ver'),
('agentes_impresion.crear','Crear agentes de impresión','Registrar agentes para contaCloudPrint','CONFIGURACION','agentes_impresion','crear'),
('agentes_impresion.codigo','Generar código de vinculación','Generar o reemplazar códigos temporales de agentes','CONFIGURACION','agentes_impresion','codigo'),
('agentes_impresion.revocar','Revocar agentes de impresión','Revocar agentes y sus credenciales','CONFIGURACION','agentes_impresion','revocar')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,
modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE r.codigo='ADMINISTRADOR' AND p.recurso='agentes_impresion'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
