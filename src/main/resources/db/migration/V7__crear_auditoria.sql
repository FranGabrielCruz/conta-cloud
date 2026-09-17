CREATE TABLE auditoria (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), empresa_id UUID NOT NULL REFERENCES empresas(id),
    usuario_id UUID, fecha_hora TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accion VARCHAR(50) NOT NULL, entidad VARCHAR(100) NOT NULL, registro_id UUID,
    detalle JSONB, direccion_ip VARCHAR(64),
    FOREIGN KEY (empresa_id, usuario_id) REFERENCES usuarios(empresa_id, id)
);
CREATE INDEX idx_auditoria_empresa_fecha ON auditoria(empresa_id, fecha_hora DESC);
