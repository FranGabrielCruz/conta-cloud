CREATE TABLE empresas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo VARCHAR(30) NOT NULL,
    nombre VARCHAR(150) NOT NULL,
    identificacion_fiscal VARCHAR(30),
    pais_codigo CHAR(2),
    zona_horaria VARCHAR(60) NOT NULL DEFAULT 'America/Santo_Domingo',
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_empresas_codigo UNIQUE (codigo)
);
