CREATE TABLE cuentas_bancarias (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    moneda_id UUID NOT NULL,
    codigo VARCHAR(40) NOT NULL,
    banco_nombre VARCHAR(120) NOT NULL,
    nombre_cuenta VARCHAR(120) NOT NULL,
    tipo_cuenta VARCHAR(20) NOT NULL,
    numero_cuenta_cifrado VARCHAR(1024) NOT NULL,
    numero_cuenta_ultimos4 VARCHAR(4) NOT NULL,
    numero_cuenta_fingerprint VARCHAR(64) NOT NULL,
    descripcion VARCHAR(500),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creado_por UUID,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_por UUID,
    CONSTRAINT ck_cuentas_bancarias_tipo
        CHECK (tipo_cuenta IN ('CHECKING','SAVINGS','OTHER')),
    CONSTRAINT fk_cuentas_bancarias_tenant_empresa
        FOREIGN KEY (tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
    CONSTRAINT fk_cuentas_bancarias_empresa_moneda
        FOREIGN KEY (empresa_id,moneda_id) REFERENCES monedas(empresa_id,id),
    CONSTRAINT uk_cuentas_bancarias_empresa_codigo UNIQUE (empresa_id,codigo),
    CONSTRAINT uk_cuentas_bancarias_numero
        UNIQUE (tenant_id,empresa_id,numero_cuenta_fingerprint)
);

CREATE INDEX idx_cuentas_bancarias_tenant_empresa_estado
    ON cuentas_bancarias(tenant_id,empresa_id,activo);
CREATE INDEX idx_cuentas_bancarias_tenant_empresa_banco_nombre
    ON cuentas_bancarias(tenant_id,empresa_id,banco_nombre,nombre_cuenta);
CREATE INDEX idx_cuentas_bancarias_moneda ON cuentas_bancarias(moneda_id);

CREATE TRIGGER trg_cuentas_bancarias_tenant_empresa
BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON cuentas_bancarias
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('cuentas_bancarias.ver','Ver cuentas bancarias','Consultar las cuentas bancarias de la empresa','CAJA_BANCOS','cuentas_bancarias','ver'),
('cuentas_bancarias.crear','Crear cuentas bancarias','Registrar cuentas bancarias para la empresa','CAJA_BANCOS','cuentas_bancarias','crear'),
('cuentas_bancarias.editar','Editar cuentas bancarias','Modificar los datos maestros de una cuenta bancaria','CAJA_BANCOS','cuentas_bancarias','editar'),
('cuentas_bancarias.desactivar','Cambiar estado de cuentas bancarias','Desactivar o reactivar cuentas bancarias','CAJA_BANCOS','cuentas_bancarias','desactivar')
ON CONFLICT(codigo) DO UPDATE SET
    nombre=EXCLUDED.nombre,
    descripcion=EXCLUDED.descripcion,
    modulo=EXCLUDED.modulo,
    recurso=EXCLUDED.recurso,
    accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id
FROM roles r
JOIN permisos p ON p.recurso='cuentas_bancarias'
WHERE r.codigo='ADMINISTRADOR'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;

INSERT INTO empresa_modulos(tenant_id,empresa_id,module_key,enabled)
SELECT e.tenant_id,e.id,'CAJA_BANCOS',TRUE
FROM empresas e
WHERE upper(e.codigo)='DEMO'
ON CONFLICT(empresa_id,module_key) DO UPDATE SET
    enabled=TRUE,
    updated_at=CURRENT_TIMESTAMP;
