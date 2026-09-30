CREATE UNIQUE INDEX IF NOT EXISTS uk_cajas_tenant_empresa_id
    ON cajas(tenant_id,empresa_id,id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_cuentas_bancarias_tenant_empresa_id
    ON cuentas_bancarias(tenant_id,empresa_id,id);

CREATE TABLE movimientos_financieros (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    tipo_movimiento VARCHAR(10) NOT NULL,
    fecha DATE NOT NULL,
    tipo_cuenta VARCHAR(20) NOT NULL,
    caja_id UUID,
    cuenta_bancaria_id UUID,
    moneda_id UUID NOT NULL,
    monto NUMERIC(19,4) NOT NULL,
    concepto VARCHAR(180) NOT NULL,
    referencia VARCHAR(100),
    descripcion VARCHAR(1000),
    estado VARCHAR(12) NOT NULL DEFAULT 'REGISTERED',
    tipo_origen VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    origen_id UUID,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creado_por UUID NOT NULL,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_por UUID NOT NULL,
    anulado_en TIMESTAMPTZ,
    anulado_por UUID,
    motivo_anulacion VARCHAR(500),
    CONSTRAINT ck_movimientos_financieros_tipo
        CHECK (tipo_movimiento IN ('INCOME','EXPENSE')),
    CONSTRAINT ck_movimientos_financieros_cuenta
        CHECK ((tipo_cuenta='CASH_REGISTER' AND caja_id IS NOT NULL AND cuenta_bancaria_id IS NULL)
            OR (tipo_cuenta='BANK_ACCOUNT' AND caja_id IS NULL AND cuenta_bancaria_id IS NOT NULL)),
    CONSTRAINT ck_movimientos_financieros_monto CHECK (monto > 0),
    CONSTRAINT ck_movimientos_financieros_estado CHECK (estado IN ('REGISTERED','VOIDED')),
    CONSTRAINT ck_movimientos_financieros_origen CHECK (tipo_origen IN
        ('MANUAL','CUSTOMER_RECEIPT','SUPPLIER_PAYMENT','TRANSFER','OPENING_BALANCE','ADJUSTMENT','OTHER')),
    CONSTRAINT ck_movimientos_financieros_anulacion CHECK (
        (estado='REGISTERED' AND anulado_en IS NULL AND anulado_por IS NULL AND motivo_anulacion IS NULL)
        OR (estado='VOIDED' AND anulado_en IS NOT NULL AND anulado_por IS NOT NULL AND motivo_anulacion IS NOT NULL)),
    CONSTRAINT fk_movimientos_financieros_tenant_empresa
        FOREIGN KEY (tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
    CONSTRAINT fk_movimientos_financieros_caja
        FOREIGN KEY (tenant_id,empresa_id,caja_id) REFERENCES cajas(tenant_id,empresa_id,id),
    CONSTRAINT fk_movimientos_financieros_cuenta_bancaria
        FOREIGN KEY (tenant_id,empresa_id,cuenta_bancaria_id)
        REFERENCES cuentas_bancarias(tenant_id,empresa_id,id),
    CONSTRAINT fk_movimientos_financieros_moneda
        FOREIGN KEY (empresa_id,moneda_id) REFERENCES monedas(empresa_id,id),
    CONSTRAINT fk_movimientos_financieros_creado_por FOREIGN KEY (creado_por) REFERENCES usuarios(id),
    CONSTRAINT fk_movimientos_financieros_actualizado_por FOREIGN KEY (actualizado_por) REFERENCES usuarios(id),
    CONSTRAINT fk_movimientos_financieros_anulado_por FOREIGN KEY (anulado_por) REFERENCES usuarios(id)
);

CREATE INDEX idx_movimientos_financieros_empresa_tipo_fecha
    ON movimientos_financieros(tenant_id,empresa_id,tipo_movimiento,fecha DESC,creado_en DESC);
CREATE INDEX idx_movimientos_financieros_empresa_estado
    ON movimientos_financieros(tenant_id,empresa_id,estado);
CREATE INDEX idx_movimientos_financieros_caja ON movimientos_financieros(caja_id);
CREATE INDEX idx_movimientos_financieros_cuenta_bancaria ON movimientos_financieros(cuenta_bancaria_id);
CREATE INDEX idx_movimientos_financieros_moneda ON movimientos_financieros(moneda_id);

CREATE TRIGGER trg_movimientos_financieros_tenant_empresa
BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON movimientos_financieros
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('ingresos.ver','Ver ingresos','Consultar los ingresos de caja y bancos','CAJA_BANCOS','ingresos','ver'),
('ingresos.crear','Crear ingresos','Registrar ingresos manuales de caja y bancos','CAJA_BANCOS','ingresos','crear'),
('ingresos.editar','Editar ingresos','Modificar ingresos manuales registrados','CAJA_BANCOS','ingresos','editar'),
('ingresos.anular','Anular ingresos','Anular ingresos conservando su historial','CAJA_BANCOS','ingresos','anular'),
('egresos.ver','Ver egresos','Consultar los egresos de caja y bancos','CAJA_BANCOS','egresos','ver'),
('egresos.crear','Crear egresos','Registrar egresos manuales de caja y bancos','CAJA_BANCOS','egresos','crear'),
('egresos.editar','Editar egresos','Modificar egresos manuales registrados','CAJA_BANCOS','egresos','editar'),
('egresos.anular','Anular egresos','Anular egresos conservando su historial','CAJA_BANCOS','egresos','anular')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,
    modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r
JOIN permisos p ON p.recurso IN ('ingresos','egresos')
WHERE r.codigo='ADMINISTRADOR'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;

INSERT INTO empresa_modulos(tenant_id,empresa_id,module_key,enabled)
SELECT e.tenant_id,e.id,'CAJA_BANCOS',TRUE FROM empresas e WHERE upper(e.codigo)='DEMO'
ON CONFLICT(empresa_id,module_key) DO UPDATE SET enabled=TRUE,updated_at=CURRENT_TIMESTAMP;
