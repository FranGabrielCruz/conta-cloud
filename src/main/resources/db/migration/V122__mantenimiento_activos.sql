CREATE TABLE activo_mantenimiento_tipo (
    codigo VARCHAR(24) PRIMARY KEY,
    nombre VARCHAR(80) NOT NULL UNIQUE
);
INSERT INTO activo_mantenimiento_tipo VALUES ('PREVENTIVO','Preventivo'),('CORRECTIVO','Correctivo');

CREATE TABLE activo_mantenimiento_secuencia (
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    ultimo BIGINT NOT NULL CHECK (ultimo>0),
    PRIMARY KEY (tenant_id,empresa_id),
    FOREIGN KEY (tenant_id,empresa_id) REFERENCES empresas(tenant_id,id)
);

CREATE TABLE activo_mantenimiento (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    sucursal_id UUID NOT NULL,
    activo_id UUID NOT NULL,
    codigo VARCHAR(24) NOT NULL,
    tipo VARCHAR(24) NOT NULL REFERENCES activo_mantenimiento_tipo(codigo),
    prioridad VARCHAR(8) NOT NULL CHECK (prioridad IN ('BAJA','MEDIA','ALTA')),
    estado VARCHAR(16) NOT NULL DEFAULT 'PROGRAMADO' CHECK (estado IN ('PROGRAMADO','EN_PROCESO','COMPLETADO','CANCELADO')),
    programada DATE NOT NULL,
    proxima DATE,
    responsable_id UUID,
    proveedor_id UUID,
    moneda_id UUID NOT NULL,
    estimado NUMERIC(19,2) NOT NULL CHECK (estimado>=0),
    referencia VARCHAR(150),
    descripcion VARCHAR(2000) NOT NULL CHECK (btrim(descripcion)<>''),
    observacion VARCHAR(2000),
    fuera_servicio BOOLEAN NOT NULL DEFAULT false,
    condicion_anterior VARCHAR(20),
    inicio TIMESTAMPTZ,
    iniciado_por UUID REFERENCES usuarios(id),
    cierre TIMESTAMPTZ,
    cerrado_por UUID REFERENCES usuarios(id),
    realizada DATE,
    real NUMERIC(19,2) CHECK (real>=0),
    trabajo VARCHAR(2000),
    motivo VARCHAR(1000),
    snapshot JSONB,
    solicitud_id UUID,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creado_por UUID NOT NULL REFERENCES usuarios(id),
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id,empresa_id,codigo),
    UNIQUE (tenant_id,empresa_id,solicitud_id),
    FOREIGN KEY (tenant_id,empresa_id,activo_id) REFERENCES activo_fijo(tenant_id,empresa_id,id),
    FOREIGN KEY (empresa_id,sucursal_id) REFERENCES sucursales(empresa_id,id),
    FOREIGN KEY (empresa_id,moneda_id) REFERENCES monedas(empresa_id,id),
    FOREIGN KEY (responsable_id,empresa_id) REFERENCES usuario_empresa(usuario_id,empresa_id),
    FOREIGN KEY (tenant_id,empresa_id,proveedor_id) REFERENCES supplier(tenant_id,empresa_id,id),
    CHECK ((estado='COMPLETADO')=(realizada IS NOT NULL AND real IS NOT NULL AND trabajo IS NOT NULL AND snapshot IS NOT NULL)),
    CHECK ((estado='CANCELADO')=(motivo IS NOT NULL)),
    CHECK ((estado IN ('COMPLETADO','CANCELADO'))=(cierre IS NOT NULL AND cerrado_por IS NOT NULL)),
    CHECK (estado NOT IN ('EN_PROCESO','COMPLETADO') OR (inicio IS NOT NULL AND iniciado_por IS NOT NULL)),
    CHECK (proxima IS NULL OR proxima>COALESCE(realizada,programada))
);
CREATE INDEX idx_activo_mantenimiento_estado ON activo_mantenimiento(tenant_id,empresa_id,sucursal_id,estado,programada);
CREATE INDEX idx_activo_mantenimiento_fecha ON activo_mantenimiento(tenant_id,empresa_id,programada DESC,id);
CREATE INDEX idx_activo_mantenimiento_activo ON activo_mantenimiento(tenant_id,empresa_id,activo_id,estado);
CREATE UNIQUE INDEX uk_activo_mantenimiento_fuera_servicio ON activo_mantenimiento(tenant_id,empresa_id,activo_id)
    WHERE estado='EN_PROCESO' AND fuera_servicio;

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion)
SELECT 'mantenimiento_activos.'||accion,nombre,nombre,'ACTIVOS_FIJOS','mantenimiento_activos',accion
FROM (VALUES ('crear','Crear mantenimiento'),('editar','Editar mantenimiento'),('iniciar','Iniciar mantenimiento'),
    ('completar','Completar mantenimiento'),('cancelar','Cancelar mantenimiento'),('imprimir','Imprimir mantenimiento')) p(accion,nombre)
ON CONFLICT(codigo) DO NOTHING;
INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE upper(r.codigo)='ADMINISTRADOR' AND p.recurso='mantenimiento_activos'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
