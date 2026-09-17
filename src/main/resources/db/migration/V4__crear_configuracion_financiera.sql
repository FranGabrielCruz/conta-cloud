CREATE TABLE monedas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), empresa_id UUID NOT NULL REFERENCES empresas(id),
    codigo_iso CHAR(3) NOT NULL, nombre VARCHAR(80) NOT NULL, simbolo VARCHAR(10) NOT NULL,
    decimales SMALLINT NOT NULL DEFAULT 2 CHECK (decimales BETWEEN 0 AND 6),
    moneda_base BOOLEAN NOT NULL DEFAULT FALSE, activo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_monedas_empresa_codigo UNIQUE (empresa_id, codigo_iso),
    CONSTRAINT uk_monedas_empresa_id UNIQUE (empresa_id, id)
);
CREATE INDEX idx_monedas_empresa ON monedas(empresa_id);
CREATE UNIQUE INDEX uk_moneda_base_empresa ON monedas(empresa_id) WHERE moneda_base = TRUE;

CREATE TABLE tasas_cambio (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), empresa_id UUID NOT NULL,
    moneda_id UUID NOT NULL, fecha DATE NOT NULL, tasa NUMERIC(19,6) NOT NULL CHECK (tasa > 0),
    activo BOOLEAN NOT NULL DEFAULT TRUE, creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (empresa_id, moneda_id) REFERENCES monedas(empresa_id, id),
    CONSTRAINT uk_tasas_empresa_moneda_fecha UNIQUE (empresa_id, moneda_id, fecha)
);
CREATE INDEX idx_tasas_empresa_fecha ON tasas_cambio(empresa_id, fecha DESC);
