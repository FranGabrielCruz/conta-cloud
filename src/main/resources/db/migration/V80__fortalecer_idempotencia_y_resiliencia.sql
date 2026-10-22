CREATE TABLE operaciones_idempotentes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    tipo_operacion VARCHAR(80) NOT NULL,
    clave_idempotencia UUID NOT NULL,
    huella_solicitud CHAR(64) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    tipo_resultado VARCHAR(100),
    resultado_id UUID,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completado_en TIMESTAMPTZ,
    CONSTRAINT fk_operacion_idempotente_empresa
        FOREIGN KEY (empresa_id, tenant_id) REFERENCES empresas(id, tenant_id),
    CONSTRAINT uk_operacion_idempotente
        UNIQUE (tenant_id, empresa_id, tipo_operacion, clave_idempotencia),
    CONSTRAINT ck_operacion_idempotente_estado
        CHECK (estado IN ('PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_operacion_idempotente_huella
        CHECK (huella_solicitud ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_operacion_idempotente_resultado
        CHECK ((estado <> 'COMPLETED') OR (resultado_id IS NOT NULL AND completado_en IS NOT NULL))
);

CREATE INDEX idx_operaciones_idempotentes_resultado
    ON operaciones_idempotentes(tenant_id, empresa_id, tipo_resultado, resultado_id)
    WHERE resultado_id IS NOT NULL;

COMMENT ON TABLE operaciones_idempotentes IS
    'Fuente compartida de idempotencia para comandos críticos; la clave se delimita por tenant, empresa y tipo de operación.';

