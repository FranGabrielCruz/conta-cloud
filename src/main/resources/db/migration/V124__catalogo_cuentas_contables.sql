CREATE TABLE cuenta_contable (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    padre_id UUID,
    codigo VARCHAR(40) NOT NULL CHECK (codigo ~ '^[0-9]+([.-][0-9]+)*$'),
    nombre VARCHAR(160) NOT NULL CHECK (btrim(nombre) <> ''),
    tipo VARCHAR(16) NOT NULL CHECK (tipo IN ('AGRUPADORA','MOVIMIENTO')),
    clasificacion VARCHAR(16) NOT NULL CHECK (clasificacion IN ('ACTIVO','PASIVO','PATRIMONIO','INGRESOS','COSTOS','GASTOS')),
    naturaleza VARCHAR(12) NOT NULL CHECK (naturaleza IN ('DEUDORA','ACREEDORA')),
    activo BOOLEAN NOT NULL DEFAULT true,
    descripcion VARCHAR(2000),
    version BIGINT NOT NULL DEFAULT 0,
    solicitud_id UUID NOT NULL,
    creado_por UUID NOT NULL,
    UNIQUE (tenant_id,empresa_id,id),
    UNIQUE (tenant_id,empresa_id,codigo),
    UNIQUE (tenant_id,empresa_id,solicitud_id),
    CHECK (padre_id IS DISTINCT FROM id),
    FOREIGN KEY (tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
    FOREIGN KEY (tenant_id,empresa_id,padre_id) REFERENCES cuenta_contable(tenant_id,empresa_id,id),
    FOREIGN KEY (empresa_id,creado_por) REFERENCES usuarios(empresa_id,id)
);
CREATE INDEX idx_cuenta_contable_padre ON cuenta_contable(tenant_id,empresa_id,padre_id,codigo);

CREATE TABLE cuenta_contable_uso (
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    cuenta_id UUID NOT NULL,
    modulo VARCHAR(80) NOT NULL,
    referencia UUID NOT NULL,
    concepto VARCHAR(160) NOT NULL,
    clase VARCHAR(12) NOT NULL CHECK (clase IN ('INTEGRACION','HISTORICO')),
    activo BOOLEAN NOT NULL DEFAULT true,
    PRIMARY KEY (tenant_id,empresa_id,cuenta_id,modulo,referencia,clase),
    FOREIGN KEY (tenant_id,empresa_id,cuenta_id) REFERENCES cuenta_contable(tenant_id,empresa_id,id)
);

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion)
SELECT 'catalogo_cuentas.'||accion,nombre,nombre,'CONTABILIDAD','catalogo_cuentas',accion
FROM (VALUES ('crear','Crear cuenta contable'),('editar','Editar cuenta contable'),
    ('desactivar','Desactivar cuenta contable'),('activar','Activar cuenta contable'),
    ('imprimir','Imprimir catálogo de cuentas')) p(accion,nombre)
ON CONFLICT(codigo) DO NOTHING;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE upper(r.codigo)='ADMINISTRADOR' AND p.recurso='catalogo_cuentas'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
