CREATE TABLE transferencias_financieras (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    fecha DATE NOT NULL,
    tipo_cuenta_origen VARCHAR(20) NOT NULL,
    caja_origen_id UUID,
    cuenta_bancaria_origen_id UUID,
    tipo_cuenta_destino VARCHAR(20) NOT NULL,
    caja_destino_id UUID,
    cuenta_bancaria_destino_id UUID,
    moneda_origen_id UUID NOT NULL,
    moneda_destino_id UUID NOT NULL,
    monto_origen NUMERIC(19,4) NOT NULL,
    monto_destino NUMERIC(19,4) NOT NULL,
    tasa_cambio NUMERIC(19,8),
    descripcion_tasa VARCHAR(180),
    referencia VARCHAR(100),
    descripcion VARCHAR(1000),
    estado VARCHAR(12) NOT NULL DEFAULT 'REGISTERED',
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creado_por UUID NOT NULL,
    anulado_en TIMESTAMPTZ,
    anulado_por UUID,
    motivo_anulacion VARCHAR(500),
    CONSTRAINT ck_transferencias_cuenta_origen CHECK (
        (tipo_cuenta_origen='CASH_REGISTER' AND caja_origen_id IS NOT NULL AND cuenta_bancaria_origen_id IS NULL)
        OR (tipo_cuenta_origen='BANK_ACCOUNT' AND caja_origen_id IS NULL AND cuenta_bancaria_origen_id IS NOT NULL)),
    CONSTRAINT ck_transferencias_cuenta_destino CHECK (
        (tipo_cuenta_destino='CASH_REGISTER' AND caja_destino_id IS NOT NULL AND cuenta_bancaria_destino_id IS NULL)
        OR (tipo_cuenta_destino='BANK_ACCOUNT' AND caja_destino_id IS NULL AND cuenta_bancaria_destino_id IS NOT NULL)),
    CONSTRAINT ck_transferencias_cuentas_diferentes CHECK (
        tipo_cuenta_origen<>tipo_cuenta_destino
        OR COALESCE(caja_origen_id,cuenta_bancaria_origen_id)<>COALESCE(caja_destino_id,cuenta_bancaria_destino_id)),
    CONSTRAINT ck_transferencias_montos CHECK (monto_origen>0 AND monto_destino>0),
    CONSTRAINT ck_transferencias_conversion CHECK (
        (moneda_origen_id=moneda_destino_id AND monto_origen=monto_destino
            AND tasa_cambio IS NULL AND descripcion_tasa IS NULL)
        OR (moneda_origen_id<>moneda_destino_id AND tasa_cambio>0 AND descripcion_tasa IS NOT NULL)),
    CONSTRAINT ck_transferencias_estado CHECK (estado IN ('REGISTERED','VOIDED')),
    CONSTRAINT ck_transferencias_anulacion CHECK (
        (estado='REGISTERED' AND anulado_en IS NULL AND anulado_por IS NULL AND motivo_anulacion IS NULL)
        OR (estado='VOIDED' AND anulado_en IS NOT NULL AND anulado_por IS NOT NULL AND motivo_anulacion IS NOT NULL)),
    CONSTRAINT fk_transferencias_tenant_empresa FOREIGN KEY (tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
    CONSTRAINT fk_transferencias_caja_origen FOREIGN KEY (tenant_id,empresa_id,caja_origen_id)
        REFERENCES cajas(tenant_id,empresa_id,id),
    CONSTRAINT fk_transferencias_banco_origen FOREIGN KEY (tenant_id,empresa_id,cuenta_bancaria_origen_id)
        REFERENCES cuentas_bancarias(tenant_id,empresa_id,id),
    CONSTRAINT fk_transferencias_caja_destino FOREIGN KEY (tenant_id,empresa_id,caja_destino_id)
        REFERENCES cajas(tenant_id,empresa_id,id),
    CONSTRAINT fk_transferencias_banco_destino FOREIGN KEY (tenant_id,empresa_id,cuenta_bancaria_destino_id)
        REFERENCES cuentas_bancarias(tenant_id,empresa_id,id),
    CONSTRAINT fk_transferencias_moneda_origen FOREIGN KEY (empresa_id,moneda_origen_id)
        REFERENCES monedas(empresa_id,id),
    CONSTRAINT fk_transferencias_moneda_destino FOREIGN KEY (empresa_id,moneda_destino_id)
        REFERENCES monedas(empresa_id,id),
    CONSTRAINT fk_transferencias_creado_por FOREIGN KEY (creado_por) REFERENCES usuarios(id),
    CONSTRAINT fk_transferencias_anulado_por FOREIGN KEY (anulado_por) REFERENCES usuarios(id)
);

CREATE INDEX idx_transferencias_empresa_fecha
    ON transferencias_financieras(tenant_id,empresa_id,fecha DESC,creado_en DESC);
CREATE INDEX idx_transferencias_empresa_estado
    ON transferencias_financieras(tenant_id,empresa_id,estado);
CREATE INDEX idx_transferencias_caja_origen ON transferencias_financieras(caja_origen_id);
CREATE INDEX idx_transferencias_banco_origen ON transferencias_financieras(cuenta_bancaria_origen_id);
CREATE INDEX idx_transferencias_caja_destino ON transferencias_financieras(caja_destino_id);
CREATE INDEX idx_transferencias_banco_destino ON transferencias_financieras(cuenta_bancaria_destino_id);

CREATE UNIQUE INDEX uk_movimientos_transferencia_direccion
    ON movimientos_financieros(tenant_id,empresa_id,origen_id,tipo_movimiento)
    WHERE tipo_origen='TRANSFER';

CREATE TRIGGER trg_transferencias_tenant_empresa
BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON transferencias_financieras
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('transferencias.ver','Ver transferencias','Consultar transferencias entre cajas y cuentas bancarias','CAJA_BANCOS','transferencias','ver'),
('transferencias.crear','Crear transferencias','Registrar transferencias entre cajas y cuentas bancarias','CAJA_BANCOS','transferencias','crear'),
('transferencias.anular','Anular transferencias','Anular transferencias conservando su historial','CAJA_BANCOS','transferencias','anular')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,
    modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r
JOIN permisos p ON p.recurso='transferencias'
WHERE r.codigo='ADMINISTRADOR'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
