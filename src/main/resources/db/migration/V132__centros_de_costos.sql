CREATE TABLE centro_costo_secuencia (
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    ultimo BIGINT NOT NULL CHECK (ultimo > 0),
    PRIMARY KEY (tenant_id,empresa_id),
    FOREIGN KEY (tenant_id,empresa_id) REFERENCES empresas(tenant_id,id)
);

CREATE TABLE centro_costo (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    padre_id UUID,
    numero BIGINT NOT NULL CHECK (numero > 0),
    codigo VARCHAR(24) NOT NULL,
    nombre VARCHAR(160) NOT NULL CHECK (btrim(nombre) <> ''),
    descripcion VARCHAR(2000),
    activo BOOLEAN NOT NULL DEFAULT true,
    version BIGINT NOT NULL DEFAULT 0,
    solicitud UUID NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
    creado_por UUID NOT NULL,
    actualizado_en TIMESTAMPTZ,
    actualizado_por UUID,
    UNIQUE (tenant_id,empresa_id,id),
    UNIQUE (tenant_id,empresa_id,codigo),
    UNIQUE (tenant_id,empresa_id,numero),
    UNIQUE (tenant_id,empresa_id,solicitud),
    FOREIGN KEY (tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
    FOREIGN KEY (tenant_id,empresa_id,padre_id) REFERENCES centro_costo(tenant_id,empresa_id,id),
    FOREIGN KEY (empresa_id,creado_por) REFERENCES usuarios(empresa_id,id),
    FOREIGN KEY (empresa_id,actualizado_por) REFERENCES usuarios(empresa_id,id),
    CHECK (id IS DISTINCT FROM padre_id)
);
CREATE INDEX idx_centro_costo_padre ON centro_costo(tenant_id,empresa_id,padre_id);
CREATE INDEX idx_centro_costo_estado ON centro_costo(tenant_id,empresa_id,activo,numero);

ALTER TABLE cuenta_contable ADD COLUMN uso_centro_costo VARCHAR(16) NOT NULL DEFAULT 'OPCIONAL',
    ADD CONSTRAINT ck_cuenta_uso_centro_costo CHECK (uso_centro_costo IN ('NO_PERMITIDO','OPCIONAL','OBLIGATORIO'));
UPDATE cuenta_contable SET uso_centro_costo='NO_PERMITIDO' WHERE tipo='AGRUPADORA';

ALTER TABLE asiento_contable_linea ADD COLUMN centro_costo_id UUID,
    ADD CONSTRAINT fk_asiento_linea_centro_costo FOREIGN KEY (tenant_id,empresa_id,centro_costo_id)
        REFERENCES centro_costo(tenant_id,empresa_id,id);
CREATE INDEX idx_asiento_linea_centro_costo ON asiento_contable_linea(tenant_id,empresa_id,centro_costo_id,asiento_id)
    WHERE centro_costo_id IS NOT NULL;

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion)
SELECT 'centros_costos.'||accion,nombre,nombre,'CONTABILIDAD','centros_costos',accion FROM
(VALUES ('crear','Crear centro de costos'),('editar','Editar centro de costos'),
    ('activar','Activar centro de costos'),('desactivar','Desactivar centro de costos')) p(accion,nombre)
ON CONFLICT(codigo) DO NOTHING;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE upper(r.codigo)='ADMINISTRADOR' AND p.recurso='centros_costos'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
