CREATE TABLE datos_empresa (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    empresa_id UUID NOT NULL UNIQUE REFERENCES empresas(id),
    nombre_comercial VARCHAR(150), direccion VARCHAR(255), telefono VARCHAR(40),
    correo VARCHAR(180), sitio_web VARCHAR(180), logo_url VARCHAR(500),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sucursales (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    empresa_id UUID NOT NULL REFERENCES empresas(id),
    codigo VARCHAR(30) NOT NULL, nombre VARCHAR(150) NOT NULL,
    direccion VARCHAR(255), telefono VARCHAR(40), principal BOOLEAN NOT NULL DEFAULT FALSE,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_sucursales_empresa_codigo UNIQUE (empresa_id, codigo),
    CONSTRAINT uk_sucursales_empresa_id UNIQUE (empresa_id, id)
);
CREATE INDEX idx_sucursales_empresa ON sucursales(empresa_id);
CREATE UNIQUE INDEX uk_sucursal_principal_empresa ON sucursales(empresa_id) WHERE principal = TRUE;
