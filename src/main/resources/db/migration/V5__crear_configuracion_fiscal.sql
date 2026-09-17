CREATE TABLE impuestos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), empresa_id UUID NOT NULL REFERENCES empresas(id),
    codigo VARCHAR(30) NOT NULL, nombre VARCHAR(100) NOT NULL,
    porcentaje NUMERIC(9,4) NOT NULL CHECK (porcentaje >= 0), tipo VARCHAR(30) NOT NULL,
    cuenta_contable VARCHAR(50), activo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_impuestos_empresa_codigo UNIQUE (empresa_id, codigo),
    CONSTRAINT uk_impuestos_empresa_id UNIQUE (empresa_id, id)
);
CREATE INDEX idx_impuestos_empresa ON impuestos(empresa_id);

CREATE TABLE tipos_comprobantes_fiscales (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), empresa_id UUID NOT NULL REFERENCES empresas(id),
    codigo VARCHAR(30) NOT NULL, nombre VARCHAR(120) NOT NULL, prefijo VARCHAR(20),
    tipo VARCHAR(40) NOT NULL, reglas_secuencia JSONB, activo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_comprobantes_empresa_codigo UNIQUE (empresa_id, codigo),
    CONSTRAINT uk_comprobantes_empresa_id UNIQUE (empresa_id, id)
);
CREATE INDEX idx_comprobantes_empresa ON tipos_comprobantes_fiscales(empresa_id);

CREATE TABLE secuencias (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), empresa_id UUID NOT NULL REFERENCES empresas(id),
    sucursal_id UUID, tipo_comprobante_id UUID, codigo VARCHAR(40) NOT NULL,
    serie VARCHAR(20), anio INTEGER, valor_actual BIGINT NOT NULL DEFAULT 0 CHECK (valor_actual >= 0),
    longitud SMALLINT NOT NULL DEFAULT 8 CHECK (longitud BETWEEN 1 AND 30),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    FOREIGN KEY (empresa_id, sucursal_id) REFERENCES sucursales(empresa_id, id),
    FOREIGN KEY (empresa_id, tipo_comprobante_id) REFERENCES tipos_comprobantes_fiscales(empresa_id, id),
    CONSTRAINT uk_secuencias_empresa_codigo UNIQUE (empresa_id, codigo),
    CONSTRAINT uk_secuencias_empresa_id UNIQUE (empresa_id, id)
);
CREATE INDEX idx_secuencias_empresa ON secuencias(empresa_id);
