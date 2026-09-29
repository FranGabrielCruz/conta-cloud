CREATE TABLE cajas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    sucursal_id UUID NOT NULL,
    moneda_id UUID NOT NULL,
    codigo VARCHAR(40) NOT NULL,
    nombre VARCHAR(120) NOT NULL,
    descripcion VARCHAR(500),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creado_por UUID,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_por UUID,
    CONSTRAINT fk_cajas_tenant_empresa FOREIGN KEY (tenant_id,empresa_id)
        REFERENCES empresas(tenant_id,id),
    CONSTRAINT fk_cajas_empresa_sucursal FOREIGN KEY (empresa_id,sucursal_id)
        REFERENCES sucursales(empresa_id,id),
    CONSTRAINT fk_cajas_empresa_moneda FOREIGN KEY (empresa_id,moneda_id)
        REFERENCES monedas(empresa_id,id),
    CONSTRAINT uk_cajas_empresa_codigo UNIQUE (empresa_id,codigo)
);

CREATE UNIQUE INDEX uk_cajas_tenant_empresa_sucursal_nombre
    ON cajas(tenant_id,empresa_id,sucursal_id,lower(btrim(nombre)));
CREATE INDEX idx_cajas_tenant_empresa_estado_nombre
    ON cajas(tenant_id,empresa_id,activo,nombre);
CREATE INDEX idx_cajas_tenant_empresa_sucursal
    ON cajas(tenant_id,empresa_id,sucursal_id);
CREATE INDEX idx_cajas_moneda ON cajas(moneda_id);

CREATE TRIGGER trg_cajas_tenant_empresa
BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON cajas
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('cajas.ver','Ver cajas','Consultar las cajas de la empresa','CAJA_BANCOS','cajas','ver'),
('cajas.crear','Crear cajas','Registrar cajas para las sucursales de la empresa','CAJA_BANCOS','cajas','crear'),
('cajas.editar','Editar cajas','Modificar los datos maestros de una caja','CAJA_BANCOS','cajas','editar'),
('cajas.desactivar','Cambiar estado de cajas','Desactivar o reactivar cajas','CAJA_BANCOS','cajas','desactivar')
ON CONFLICT(codigo) DO NOTHING;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id
FROM roles r
JOIN permisos p ON p.recurso='cajas'
WHERE r.codigo='ADMINISTRADOR'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;

-- La empresa de demostración debe exponer siempre las opciones nuevas para su
-- administrador, conservando la autorización normal por módulo, rol y permiso.
INSERT INTO empresa_modulos(tenant_id,empresa_id,module_key,enabled)
SELECT e.tenant_id,e.id,'CAJA_BANCOS',TRUE
FROM empresas e
WHERE upper(e.codigo)='DEMO'
ON CONFLICT(empresa_id,module_key) DO UPDATE SET
    enabled=TRUE,
    updated_at=CURRENT_TIMESTAMP;

