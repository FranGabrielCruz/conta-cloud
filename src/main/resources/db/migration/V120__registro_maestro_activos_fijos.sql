CREATE TABLE activo_fijo_categoria (
    codigo VARCHAR(24) PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL UNIQUE
);

INSERT INTO activo_fijo_categoria(codigo,nombre) VALUES
    ('TECNOLOGIA','Equipos de tecnología'), ('VEHICULOS','Vehículos'),
    ('MAQUINARIA','Maquinaria y herramientas'), ('MOBILIARIO','Mobiliario'),
    ('OFICINA','Equipos de oficina'), ('EDIFICIOS','Edificios'),
    ('TERRENOS','Terrenos'), ('OTROS','Otros bienes tangibles');

CREATE TABLE activo_fijo_secuencia (
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    ultimo BIGINT NOT NULL CHECK (ultimo > 0),
    PRIMARY KEY (tenant_id,empresa_id),
    FOREIGN KEY (tenant_id,empresa_id) REFERENCES empresas(tenant_id,id)
);

CREATE TABLE activo_fijo (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    codigo VARCHAR(24) NOT NULL,
    nombre VARCHAR(180) NOT NULL CHECK (btrim(nombre)<>''),
    categoria VARCHAR(24) NOT NULL REFERENCES activo_fijo_categoria(codigo),
    marca VARCHAR(100),
    modelo VARCHAR(100),
    serie VARCHAR(120),
    fecha_adquisicion DATE NOT NULL,
    costo NUMERIC(19,2) NOT NULL CHECK (costo>0),
    moneda_id UUID NOT NULL,
    proveedor_id UUID,
    factura_id UUID,
    referencia_compra VARCHAR(100),
    sucursal_id UUID NOT NULL,
    ubicacion VARCHAR(150),
    responsable_id UUID,
    depreciable BOOLEAN NOT NULL,
    metodo VARCHAR(24),
    vida_meses INTEGER,
    residual NUMERIC(19,2),
    inicio_depreciacion DATE,
    observacion VARCHAR(1000),
    estado VARCHAR(16) NOT NULL DEFAULT 'ACTIVO' CHECK (estado IN ('ACTIVO','INACTIVO','BAJA')),
    condicion VARCHAR(20) CHECK (condicion IN ('MANTENIMIENTO','FUERA_SERVICIO')),
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (tenant_id,empresa_id,id),
    UNIQUE (tenant_id,empresa_id,codigo),
    FOREIGN KEY (tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
    FOREIGN KEY (empresa_id,sucursal_id) REFERENCES sucursales(empresa_id,id),
    FOREIGN KEY (empresa_id,moneda_id) REFERENCES monedas(empresa_id,id),
    FOREIGN KEY (tenant_id,empresa_id,proveedor_id) REFERENCES supplier(tenant_id,empresa_id,id),
    FOREIGN KEY (tenant_id,empresa_id,factura_id) REFERENCES purchase_invoice(tenant_id,empresa_id,id),
    FOREIGN KEY (responsable_id,empresa_id) REFERENCES usuario_empresa(usuario_id,empresa_id),
    CHECK (factura_id IS NULL OR proveedor_id IS NOT NULL),
    CHECK (
        (NOT depreciable AND metodo IS NULL AND vida_meses IS NULL AND residual IS NULL AND inicio_depreciacion IS NULL)
        OR (depreciable AND metodo IS NOT NULL AND metodo IN ('LINEA_RECTA')
            AND vida_meses IS NOT NULL AND vida_meses BETWEEN 1 AND 1200
            AND residual IS NOT NULL AND residual>=0 AND residual<costo
            AND inicio_depreciacion IS NOT NULL AND inicio_depreciacion>=fecha_adquisicion)
    )
);
CREATE INDEX idx_activo_fijo_sucursal ON activo_fijo(tenant_id,empresa_id,sucursal_id,codigo);
CREATE INDEX idx_activo_fijo_filtros ON activo_fijo(tenant_id,empresa_id,categoria,estado);

CREATE TABLE activo_fijo_asignacion (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    activo_id UUID NOT NULL,
    version BIGINT NOT NULL,
    sucursal_anterior_id UUID,
    sucursal_id UUID NOT NULL,
    anterior JSONB,
    nueva JSONB NOT NULL,
    usuario_id UUID NOT NULL REFERENCES usuarios(id),
    usuario_nombre VARCHAR(220) NOT NULL,
    fecha TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id,empresa_id,activo_id,version),
    FOREIGN KEY (tenant_id,empresa_id,activo_id) REFERENCES activo_fijo(tenant_id,empresa_id,id),
    FOREIGN KEY (empresa_id,sucursal_anterior_id) REFERENCES sucursales(empresa_id,id),
    FOREIGN KEY (empresa_id,sucursal_id) REFERENCES sucursales(empresa_id,id)
);

-- Los procesos especializados registrarán aquí únicamente movimientos efectivos.
CREATE TABLE activo_fijo_movimiento (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    activo_id UUID NOT NULL,
    sucursal_id UUID NOT NULL,
    tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('DEPRECIACION','MANTENIMIENTO','AJUSTE','BAJA')),
    fecha DATE NOT NULL,
    descripcion VARCHAR(500) NOT NULL,
    importe NUMERIC(19,2) NOT NULL CHECK (importe>=0),
    FOREIGN KEY (tenant_id,empresa_id,activo_id) REFERENCES activo_fijo(tenant_id,empresa_id,id),
    FOREIGN KEY (empresa_id,sucursal_id) REFERENCES sucursales(empresa_id,id)
);
CREATE INDEX idx_activo_fijo_movimiento ON activo_fijo_movimiento(tenant_id,empresa_id,activo_id,fecha,id);
CREATE UNIQUE INDEX uk_activo_fijo_baja ON activo_fijo_movimiento(tenant_id,empresa_id,activo_id) WHERE tipo='BAJA';

CREATE FUNCTION bloquear_activo_fijo_movimiento() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    UPDATE activo_fijo SET version=version+1
    WHERE tenant_id=NEW.tenant_id AND empresa_id=NEW.empresa_id AND id=NEW.activo_id;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_bloquear_activo_fijo_movimiento
BEFORE INSERT ON activo_fijo_movimiento FOR EACH ROW EXECUTE FUNCTION bloquear_activo_fijo_movimiento();

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
    ('activos_fijos.crear','Crear activos fijos','Registrar activos fijos','ACTIVOS_FIJOS','activos_fijos','crear'),
    ('activos_fijos.editar','Editar activos fijos','Editar el registro maestro','ACTIVOS_FIJOS','activos_fijos','editar'),
    ('activos_fijos.reportar','Generar reporte de activos fijos','Consultar el reporte maestro','ACTIVOS_FIJOS','activos_fijos','reportar')
ON CONFLICT(codigo) DO NOTHING;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE upper(r.codigo)='ADMINISTRADOR' AND p.codigo IN
    ('activos_fijos.crear','activos_fijos.editar','activos_fijos.reportar')
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
