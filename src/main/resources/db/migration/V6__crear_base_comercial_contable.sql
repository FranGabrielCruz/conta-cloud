CREATE TABLE condiciones_pago (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), empresa_id UUID NOT NULL REFERENCES empresas(id),
    codigo VARCHAR(30) NOT NULL, nombre VARCHAR(100) NOT NULL, dias INTEGER NOT NULL CHECK (dias >= 0),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_condiciones_empresa_codigo UNIQUE (empresa_id, codigo)
);
CREATE INDEX idx_condiciones_empresa ON condiciones_pago(empresa_id);

CREATE TABLE periodos_fiscales (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), empresa_id UUID NOT NULL REFERENCES empresas(id),
    anio INTEGER NOT NULL, mes SMALLINT NOT NULL CHECK (mes BETWEEN 1 AND 12),
    fecha_inicial DATE NOT NULL, fecha_final DATE NOT NULL, estado VARCHAR(20) NOT NULL,
    fecha_cierre TIMESTAMPTZ, usuario_cierre_id UUID,
    CHECK (fecha_final >= fecha_inicial), CHECK (estado IN ('ABIERTO', 'CERRADO', 'BLOQUEADO')),
    FOREIGN KEY (empresa_id, usuario_cierre_id) REFERENCES usuarios(empresa_id, id),
    CONSTRAINT uk_periodos_empresa_anio_mes UNIQUE (empresa_id, anio, mes)
);
CREATE INDEX idx_periodos_empresa ON periodos_fiscales(empresa_id);

CREATE TABLE configuracion_contable (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), empresa_id UUID NOT NULL UNIQUE REFERENCES empresas(id),
    moneda_base_id UUID NOT NULL, mes_inicio_fiscal SMALLINT NOT NULL DEFAULT 1 CHECK (mes_inicio_fiscal BETWEEN 1 AND 12),
    metodo_numeracion VARCHAR(30) NOT NULL DEFAULT 'POR_SECUENCIA', decimales SMALLINT NOT NULL DEFAULT 2,
    configuracion_fiscal JSONB, parametros JSONB,
    FOREIGN KEY (empresa_id, moneda_base_id) REFERENCES monedas(empresa_id, id)
);
