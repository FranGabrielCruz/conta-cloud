CREATE TABLE cash_movement_concepts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    movement_type VARCHAR(10) NOT NULL,
    requires_observation BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_cash_movement_concept_empresa
        FOREIGN KEY (tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
    CONSTRAINT ck_cash_movement_concept_type CHECK (movement_type IN ('INCOME','EXPENSE')),
    CONSTRAINT uk_cash_movement_concept_code UNIQUE (tenant_id,empresa_id,code),
    CONSTRAINT uk_cash_movement_concept_id UNIQUE (tenant_id,empresa_id,id)
);

CREATE INDEX idx_cash_movement_concept_lookup
    ON cash_movement_concepts(tenant_id,empresa_id,movement_type,active,name);

INSERT INTO cash_movement_concepts(tenant_id,empresa_id,code,name,movement_type,requires_observation)
SELECT e.tenant_id,e.id,v.code,v.name,v.movement_type,v.requires_observation
FROM empresas e CROSS JOIN (VALUES
    ('CASH_REPLENISHMENT','Reposición de efectivo','INCOME',FALSE),
    ('CASH_REIMBURSEMENT','Reintegro','INCOME',FALSE),
    ('AUTHORIZED_EXTRA_IN','Entrada extraordinaria autorizada','INCOME',TRUE),
    ('CASH_WITHDRAWAL','Retiro de efectivo','EXPENSE',TRUE),
    ('ADMINISTRATION_DELIVERY','Entrega a administración','EXPENSE',TRUE)
) v(code,name,movement_type,requires_observation)
ON CONFLICT(tenant_id,empresa_id,code) DO NOTHING;

CREATE TABLE cash_movement_sequence (
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    current_value BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY(tenant_id,empresa_id),
    CONSTRAINT fk_cash_movement_sequence_empresa
        FOREIGN KEY (tenant_id,empresa_id) REFERENCES empresas(tenant_id,id)
);

ALTER TABLE movimientos_financieros
    ADD COLUMN cash_movement_number VARCHAR(30),
    ADD COLUMN cash_concept_id UUID,
    ADD COLUMN reversal_of_movement_id UUID,
    ADD COLUMN reversal_reason VARCHAR(500);

ALTER TABLE movimientos_financieros
    ADD CONSTRAINT fk_financial_movement_cash_concept
        FOREIGN KEY (tenant_id,empresa_id,cash_concept_id)
        REFERENCES cash_movement_concepts(tenant_id,empresa_id,id),
    ADD CONSTRAINT fk_financial_movement_reversal
        FOREIGN KEY (reversal_of_movement_id) REFERENCES movimientos_financieros(id),
    ADD CONSTRAINT ck_financial_movement_reversal_data CHECK (
        (tipo_origen <> 'REVERSAL' AND reversal_of_movement_id IS NULL AND reversal_reason IS NULL)
        OR (tipo_origen = 'REVERSAL' AND reversal_of_movement_id IS NOT NULL AND reversal_reason IS NOT NULL)
    );

ALTER TABLE movimientos_financieros DROP CONSTRAINT ck_movimientos_financieros_origen;
ALTER TABLE movimientos_financieros ADD CONSTRAINT ck_movimientos_financieros_origen CHECK (tipo_origen IN
 ('MANUAL','CUSTOMER_RECEIPT','SUPPLIER_PAYMENT','SALES_INVOICE','SALES_PAYMENT','TRANSFER',
  'OPENING_BALANCE','ADJUSTMENT','REVERSAL','OTHER'));

CREATE UNIQUE INDEX uk_financial_movement_cash_number
    ON movimientos_financieros(tenant_id,empresa_id,cash_movement_number)
    WHERE cash_movement_number IS NOT NULL;
CREATE UNIQUE INDEX uk_financial_movement_single_reversal
    ON movimientos_financieros(tenant_id,empresa_id,reversal_of_movement_id)
    WHERE reversal_of_movement_id IS NOT NULL;
CREATE INDEX idx_financial_movement_cash_session_order
    ON movimientos_financieros(tenant_id,empresa_id,cash_register_session_id,creado_en,id)
    WHERE tipo_cuenta='CASH_REGISTER' AND payment_method='CASH';

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('movimientos_caja.ver','Ver movimientos de caja','Consultar movimientos de efectivo del turno','CAJA_BANCOS','movimientos_caja','ver'),
('movimientos_caja.crear','Crear movimientos de caja','Registrar entradas y salidas manuales autorizadas','CAJA_BANCOS','movimientos_caja','crear'),
('movimientos_caja.revertir','Revertir movimientos de caja','Crear reversiones controladas de movimientos manuales','CAJA_BANCOS','movimientos_caja','revertir')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,
    modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE upper(r.codigo)='ADMINISTRADOR' AND p.recurso='movimientos_caja'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
