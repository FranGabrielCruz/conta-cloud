CREATE TABLE activo_baja_tipo (
    codigo VARCHAR(24) PRIMARY KEY,
    nombre VARCHAR(80) NOT NULL UNIQUE
);
INSERT INTO activo_baja_tipo VALUES ('VENTA','Venta'),('OBSOLESCENCIA','Obsolescencia'),
    ('DANO','Daño / destrucción'),('PERDIDA','Pérdida'),('ROBO','Robo'),('DONACION','Donación'),
    ('FIN_VIDA','Fin de vida útil'),('OTRO','Otro');

CREATE TABLE activo_baja_secuencia (
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    ultimo BIGINT NOT NULL CHECK (ultimo>0),
    PRIMARY KEY (tenant_id,empresa_id),
    FOREIGN KEY (tenant_id,empresa_id) REFERENCES empresas(tenant_id,id)
);

CREATE TABLE activo_baja (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    sucursal_id UUID NOT NULL,
    activo_id UUID NOT NULL,
    codigo VARCHAR(24) NOT NULL,
    tipo VARCHAR(24) NOT NULL REFERENCES activo_baja_tipo(codigo),
    fecha DATE NOT NULL,
    estado VARCHAR(16) NOT NULL DEFAULT 'BORRADOR' CHECK (estado IN ('BORRADOR','CONFIRMADA')),
    motivo VARCHAR(2000) NOT NULL CHECK (btrim(motivo)<>''),
    obtenido NUMERIC(19,2) NOT NULL CHECK (obtenido>=0),
    moneda_id UUID NOT NULL,
    referencia VARCHAR(150),
    factura_id UUID,
    observacion VARCHAR(2000),
    costo NUMERIC(19,2),
    depreciacion NUMERIC(19,2),
    libros NUMERIC(19,2) CHECK (libros>=0),
    snapshot JSONB,
    confirmado_en TIMESTAMPTZ,
    confirmado_por UUID REFERENCES usuarios(id),
    solicitud_id UUID,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
    creado_por UUID NOT NULL REFERENCES usuarios(id),
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id,empresa_id,codigo),
    UNIQUE (tenant_id,empresa_id,solicitud_id),
    UNIQUE (tenant_id,empresa_id,id,activo_id),
    FOREIGN KEY (tenant_id,empresa_id,activo_id) REFERENCES activo_fijo(tenant_id,empresa_id,id),
    FOREIGN KEY (empresa_id,sucursal_id) REFERENCES sucursales(empresa_id,id),
    FOREIGN KEY (empresa_id,moneda_id) REFERENCES monedas(empresa_id,id),
    FOREIGN KEY (tenant_id,empresa_id,factura_id) REFERENCES sales_invoice(tenant_id,empresa_id,id),
    CHECK (factura_id IS NULL OR tipo='VENTA'),
    CHECK ((estado='CONFIRMADA')=(snapshot IS NOT NULL AND costo IS NOT NULL AND depreciacion IS NOT NULL
        AND libros IS NOT NULL AND confirmado_en IS NOT NULL AND confirmado_por IS NOT NULL AND solicitud_id IS NOT NULL))
);
CREATE UNIQUE INDEX uk_activo_baja_confirmada ON activo_baja(tenant_id,empresa_id,activo_id) WHERE estado='CONFIRMADA';
CREATE INDEX idx_activo_baja_fecha ON activo_baja(tenant_id,empresa_id,fecha DESC,id);
CREATE INDEX idx_activo_baja_sucursal_estado ON activo_baja(tenant_id,empresa_id,sucursal_id,estado,fecha);

ALTER TABLE activo_fijo_movimiento ADD COLUMN baja_id UUID;
ALTER TABLE activo_fijo_movimiento ADD CONSTRAINT fk_activo_movimiento_baja
    FOREIGN KEY (tenant_id,empresa_id,baja_id,activo_id) REFERENCES activo_baja(tenant_id,empresa_id,id,activo_id);

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion)
SELECT 'bajas_activos.'||accion,nombre,nombre,'ACTIVOS_FIJOS','bajas_activos',accion
FROM (VALUES ('crear','Crear baja'),('editar','Editar baja'),('confirmar','Confirmar baja'),
    ('imprimir','Imprimir baja')) p(accion,nombre)
ON CONFLICT(codigo) DO NOTHING;
INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE upper(r.codigo)='ADMINISTRADOR' AND p.recurso='bajas_activos'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
