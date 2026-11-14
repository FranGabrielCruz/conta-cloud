CREATE TABLE activo_depreciacion_proceso (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    numero BIGINT NOT NULL CHECK (numero > 0),
    periodo DATE NOT NULL CHECK (extract(day FROM periodo)=1),
    fecha DATE NOT NULL,
    procesado_en TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
    usuario_id UUID NOT NULL REFERENCES usuarios(id),
    usuario_nombre VARCHAR(220) NOT NULL,
    solicitud_id UUID NOT NULL,
    empresa_nombre VARCHAR(180) NOT NULL,
    rnc VARCHAR(80),
    direccion VARCHAR(255),
    telefono VARCHAR(40),
    pais VARCHAR(6),
    logo VARCHAR(500),
    UNIQUE (tenant_id,empresa_id,id),
    UNIQUE (tenant_id,empresa_id,periodo),
    UNIQUE (tenant_id,empresa_id,numero),
    UNIQUE (tenant_id,empresa_id,solicitud_id),
    FOREIGN KEY (tenant_id,empresa_id) REFERENCES empresas(tenant_id,id)
);

CREATE TABLE activo_depreciacion_detalle (
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    proceso_id UUID NOT NULL,
    activo_id UUID NOT NULL,
    sucursal_id UUID NOT NULL,
    codigo VARCHAR(24) NOT NULL,
    nombre VARCHAR(180) NOT NULL,
    moneda VARCHAR(6) NOT NULL,
    costo NUMERIC(19,2) NOT NULL,
    base NUMERIC(19,2) NOT NULL,
    anterior NUMERIC(19,2) NOT NULL,
    importe NUMERIC(19,2) NOT NULL CHECK (importe>0),
    libros NUMERIC(19,2) NOT NULL CHECK (libros>=0),
    PRIMARY KEY (tenant_id,empresa_id,proceso_id,activo_id),
    FOREIGN KEY (tenant_id,empresa_id,proceso_id) REFERENCES activo_depreciacion_proceso(tenant_id,empresa_id,id),
    FOREIGN KEY (tenant_id,empresa_id,activo_id) REFERENCES activo_fijo(tenant_id,empresa_id,id),
    FOREIGN KEY (empresa_id,sucursal_id) REFERENCES sucursales(empresa_id,id)
);

ALTER TABLE activo_fijo_movimiento ADD COLUMN depreciacion_proceso_id UUID;
ALTER TABLE activo_fijo_movimiento ADD CONSTRAINT fk_activo_movimiento_depreciacion
    FOREIGN KEY (tenant_id,empresa_id,depreciacion_proceso_id,activo_id)
    REFERENCES activo_depreciacion_detalle(tenant_id,empresa_id,proceso_id,activo_id);
CREATE UNIQUE INDEX uk_activo_movimiento_depreciacion
    ON activo_fijo_movimiento(tenant_id,empresa_id,depreciacion_proceso_id,activo_id)
    WHERE depreciacion_proceso_id IS NOT NULL;

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
    ('depreciaciones.procesar','Procesar depreciación','Calcular y procesar la depreciación mensual','ACTIVOS_FIJOS','depreciaciones','procesar'),
    ('depreciaciones.pdf','Ver PDF de depreciación','Consultar el PDF histórico del período','ACTIVOS_FIJOS','depreciaciones','pdf')
ON CONFLICT(codigo) DO NOTHING;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE upper(r.codigo)='ADMINISTRADOR' AND p.codigo IN ('depreciaciones.procesar','depreciaciones.pdf')
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
