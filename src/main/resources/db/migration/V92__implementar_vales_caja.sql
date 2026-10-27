CREATE TABLE cash_voucher_concepts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    requires_reference BOOLEAN NOT NULL DEFAULT FALSE,
    requires_observation BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_cash_voucher_concept_empresa FOREIGN KEY (tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
    CONSTRAINT uk_cash_voucher_concept_code UNIQUE (tenant_id,empresa_id,code),
    CONSTRAINT uk_cash_voucher_concept_id UNIQUE (tenant_id,empresa_id,id)
);

CREATE INDEX idx_cash_voucher_concept_lookup
    ON cash_voucher_concepts(tenant_id,empresa_id,active,name);

INSERT INTO cash_voucher_concepts(tenant_id,empresa_id,code,name,requires_reference,requires_observation)
SELECT e.tenant_id,e.id,v.code,v.name,v.requires_reference,v.requires_observation
FROM empresas e CROSS JOIN (VALUES
    ('MINOR_PURCHASE','Compra menor',TRUE,FALSE),
    ('TRANSPORT','Transporte',FALSE,FALSE),
    ('MESSAGING','Mensajería',FALSE,FALSE),
    ('MAINTENANCE','Mantenimiento',TRUE,FALSE),
    ('SUPPLIES','Suministros',TRUE,FALSE),
    ('OPERATING_EXPENSE','Gasto operativo',FALSE,FALSE),
    ('OTHER_AUTHORIZED','Otro gasto autorizado',FALSE,TRUE)
) v(code,name,requires_reference,requires_observation)
ON CONFLICT(tenant_id,empresa_id,code) DO NOTHING;

CREATE TABLE cash_voucher_sequence (
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    current_value BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY(tenant_id,empresa_id),
    CONSTRAINT fk_cash_voucher_sequence_empresa FOREIGN KEY (tenant_id,empresa_id) REFERENCES empresas(tenant_id,id)
);

CREATE TABLE cash_voucher (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    cash_register_id UUID NOT NULL,
    cash_session_id UUID NOT NULL,
    currency_id UUID NOT NULL,
    concept_id UUID NOT NULL,
    voucher_number VARCHAR(30) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    concept_snapshot VARCHAR(120) NOT NULL,
    beneficiary_snapshot VARCHAR(180) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    reference VARCHAR(100),
    observation VARCHAR(500),
    status VARCHAR(12) NOT NULL,
    branch_snapshot VARCHAR(150) NOT NULL,
    cash_register_snapshot VARCHAR(120) NOT NULL,
    currency_snapshot VARCHAR(3) NOT NULL,
    created_by UUID NOT NULL,
    created_by_snapshot VARCHAR(180) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    movement_id UUID,
    voided_by UUID,
    voided_by_snapshot VARCHAR(180),
    voided_at TIMESTAMPTZ,
    void_reason VARCHAR(500),
    reversal_movement_id UUID,
    idempotency_key UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_cash_voucher_empresa FOREIGN KEY (tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
    CONSTRAINT fk_cash_voucher_cash FOREIGN KEY (tenant_id,empresa_id,cash_register_id) REFERENCES cajas(tenant_id,empresa_id,id),
    CONSTRAINT fk_cash_voucher_concept FOREIGN KEY (tenant_id,empresa_id,concept_id) REFERENCES cash_voucher_concepts(tenant_id,empresa_id,id),
    CONSTRAINT fk_cash_voucher_movement FOREIGN KEY (tenant_id,empresa_id,movement_id) REFERENCES movimientos_financieros(tenant_id,empresa_id,id),
    CONSTRAINT fk_cash_voucher_reversal FOREIGN KEY (tenant_id,empresa_id,reversal_movement_id) REFERENCES movimientos_financieros(tenant_id,empresa_id,id),
    CONSTRAINT ck_cash_voucher_amount CHECK (amount > 0),
    CONSTRAINT ck_cash_voucher_status CHECK (status IN ('CONFIRMED','VOIDED')),
    CONSTRAINT ck_cash_voucher_void CHECK (
      (status='CONFIRMED' AND voided_by IS NULL AND voided_at IS NULL AND void_reason IS NULL AND reversal_movement_id IS NULL)
      OR (status='VOIDED' AND voided_by IS NOT NULL AND voided_at IS NOT NULL AND void_reason IS NOT NULL AND reversal_movement_id IS NOT NULL)
    ),
    CONSTRAINT uk_cash_voucher_number UNIQUE (tenant_id,empresa_id,voucher_number),
    CONSTRAINT uk_cash_voucher_idempotency UNIQUE (tenant_id,empresa_id,idempotency_key),
    CONSTRAINT uk_cash_voucher_movement UNIQUE (tenant_id,empresa_id,movement_id),
    CONSTRAINT uk_cash_voucher_reversal UNIQUE (tenant_id,empresa_id,reversal_movement_id)
);

CREATE INDEX idx_cash_voucher_session_order ON cash_voucher(tenant_id,empresa_id,cash_session_id,occurred_at DESC,id DESC);
CREATE INDEX idx_cash_voucher_admin_order ON cash_voucher(tenant_id,empresa_id,occurred_at DESC,id DESC);
CREATE INDEX idx_cash_voucher_filters ON cash_voucher(tenant_id,empresa_id,branch_id,cash_register_id,created_by,status);

ALTER TABLE movimientos_financieros DROP CONSTRAINT ck_movimientos_financieros_origen;
ALTER TABLE movimientos_financieros ADD CONSTRAINT ck_movimientos_financieros_origen CHECK (tipo_origen IN
 ('MANUAL','CUSTOMER_RECEIPT','SUPPLIER_PAYMENT','SALES_INVOICE','SALES_PAYMENT','TRANSFER',
  'OPENING_BALANCE','ADJUSTMENT','REVERSAL','CASH_VOUCHER','OTHER'));

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('vales_caja.ver','Ver vales de caja','Consultar vales de caja','CAJA_BANCOS','vales_caja','ver'),
('vales_caja.crear','Crear vales de caja','Confirmar salidas de efectivo mediante vales','CAJA_BANCOS','vales_caja','crear'),
('vales_caja.imprimir','Imprimir vales de caja','Generar el comprobante PDF de un vale','CAJA_BANCOS','vales_caja','imprimir'),
('vales_caja.anular','Anular vales de caja','Anular vales mediante un movimiento inverso','CAJA_BANCOS','vales_caja','anular')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,
    modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE upper(r.codigo)='ADMINISTRADOR' AND p.recurso='vales_caja'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
