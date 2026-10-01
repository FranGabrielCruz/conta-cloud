CREATE TABLE bank_reconciliation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    bank_account_id UUID NOT NULL,
    currency_id UUID NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    bank_opening_balance NUMERIC(19,4) NOT NULL,
    bank_closing_balance NUMERIC(19,4) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    finalized_at TIMESTAMPTZ,
    finalized_by UUID,
    voided_at TIMESTAMPTZ,
    voided_by UUID,
    void_reason VARCHAR(500),
    CONSTRAINT ck_bank_reconciliation_dates CHECK (start_date<=end_date),
    CONSTRAINT ck_bank_reconciliation_status CHECK (status IN ('IN_PROGRESS','FINALIZED','VOIDED')),
    CONSTRAINT ck_bank_reconciliation_lifecycle CHECK (
        (status='IN_PROGRESS' AND finalized_at IS NULL AND finalized_by IS NULL AND voided_at IS NULL AND voided_by IS NULL AND void_reason IS NULL)
        OR (status='FINALIZED' AND finalized_at IS NOT NULL AND finalized_by IS NOT NULL AND voided_at IS NULL AND voided_by IS NULL AND void_reason IS NULL)
        OR (status='VOIDED' AND voided_at IS NOT NULL AND voided_by IS NOT NULL AND void_reason IS NOT NULL)),
    CONSTRAINT fk_bank_reconciliation_tenant_empresa FOREIGN KEY (tenant_id,empresa_id)
        REFERENCES empresas(tenant_id,id),
    CONSTRAINT fk_bank_reconciliation_account FOREIGN KEY (tenant_id,empresa_id,bank_account_id)
        REFERENCES cuentas_bancarias(tenant_id,empresa_id,id),
    CONSTRAINT fk_bank_reconciliation_currency FOREIGN KEY (empresa_id,currency_id)
        REFERENCES monedas(empresa_id,id),
    CONSTRAINT fk_bank_reconciliation_created_by FOREIGN KEY (created_by) REFERENCES usuarios(id),
    CONSTRAINT fk_bank_reconciliation_finalized_by FOREIGN KEY (finalized_by) REFERENCES usuarios(id),
    CONSTRAINT fk_bank_reconciliation_voided_by FOREIGN KEY (voided_by) REFERENCES usuarios(id)
);

CREATE UNIQUE INDEX uk_bank_reconciliation_tenant_empresa_id
    ON bank_reconciliation(tenant_id,empresa_id,id);
CREATE INDEX idx_bank_reconciliation_tenant_empresa
    ON bank_reconciliation(tenant_id,empresa_id);
CREATE INDEX idx_bank_reconciliation_account_period
    ON bank_reconciliation(tenant_id,empresa_id,bank_account_id,start_date,end_date);
CREATE INDEX idx_bank_reconciliation_status
    ON bank_reconciliation(tenant_id,empresa_id,status);

CREATE TABLE bank_statement_movement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    bank_reconciliation_id UUID NOT NULL,
    bank_account_id UUID NOT NULL,
    movement_date DATE NOT NULL,
    direction VARCHAR(10) NOT NULL,
    description VARCHAR(180) NOT NULL,
    reference VARCHAR(100),
    amount NUMERIC(19,4) NOT NULL,
    source_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    source_reference VARCHAR(250),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    CONSTRAINT ck_bank_statement_movement_direction CHECK (direction IN ('INFLOW','OUTFLOW')),
    CONSTRAINT ck_bank_statement_movement_amount CHECK (amount>0),
    CONSTRAINT ck_bank_statement_movement_source CHECK (source_type IN ('MANUAL','CSV','EXCEL','BANK_API','OTHER')),
    CONSTRAINT fk_bank_statement_reconciliation FOREIGN KEY (tenant_id,empresa_id,bank_reconciliation_id)
        REFERENCES bank_reconciliation(tenant_id,empresa_id,id),
    CONSTRAINT fk_bank_statement_account FOREIGN KEY (tenant_id,empresa_id,bank_account_id)
        REFERENCES cuentas_bancarias(tenant_id,empresa_id,id),
    CONSTRAINT fk_bank_statement_created_by FOREIGN KEY (created_by) REFERENCES usuarios(id)
);

CREATE UNIQUE INDEX uk_bank_statement_tenant_empresa_id
    ON bank_statement_movement(tenant_id,empresa_id,id);
CREATE INDEX idx_bank_statement_reconciliation
    ON bank_statement_movement(tenant_id,empresa_id,bank_reconciliation_id,movement_date);
CREATE INDEX idx_bank_statement_account_direction
    ON bank_statement_movement(tenant_id,empresa_id,bank_account_id,direction,movement_date);

CREATE UNIQUE INDEX IF NOT EXISTS uk_financial_movement_tenant_empresa_id
    ON movimientos_financieros(tenant_id,empresa_id,id);

CREATE TABLE bank_reconciliation_match (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    bank_reconciliation_id UUID NOT NULL,
    financial_movement_id UUID NOT NULL,
    bank_statement_movement_id UUID NOT NULL,
    matched_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    matched_by UUID NOT NULL,
    CONSTRAINT fk_bank_match_reconciliation FOREIGN KEY (tenant_id,empresa_id,bank_reconciliation_id)
        REFERENCES bank_reconciliation(tenant_id,empresa_id,id),
    CONSTRAINT fk_bank_match_financial_movement FOREIGN KEY (tenant_id,empresa_id,financial_movement_id)
        REFERENCES movimientos_financieros(tenant_id,empresa_id,id),
    CONSTRAINT fk_bank_match_statement_movement FOREIGN KEY (tenant_id,empresa_id,bank_statement_movement_id)
        REFERENCES bank_statement_movement(tenant_id,empresa_id,id),
    CONSTRAINT fk_bank_match_user FOREIGN KEY (matched_by) REFERENCES usuarios(id),
    CONSTRAINT uk_bank_match_financial UNIQUE (financial_movement_id),
    CONSTRAINT uk_bank_match_statement UNIQUE (bank_statement_movement_id)
);

CREATE INDEX idx_bank_match_reconciliation
    ON bank_reconciliation_match(tenant_id,empresa_id,bank_reconciliation_id);

CREATE TRIGGER trg_bank_reconciliation_tenant_empresa
BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON bank_reconciliation
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();
CREATE TRIGGER trg_bank_statement_tenant_empresa
BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON bank_statement_movement
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();
CREATE TRIGGER trg_bank_match_tenant_empresa
BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON bank_reconciliation_match
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('conciliacion_bancaria.ver','Ver conciliación bancaria','Consultar conciliaciones bancarias','CAJA_BANCOS','conciliacion_bancaria','ver'),
('conciliacion_bancaria.crear','Crear conciliación bancaria','Crear períodos de conciliación bancaria','CAJA_BANCOS','conciliacion_bancaria','crear'),
('conciliacion_bancaria.conciliar','Conciliar movimientos bancarios','Agregar líneas bancarias y asociar o desconciliar movimientos','CAJA_BANCOS','conciliacion_bancaria','conciliar'),
('conciliacion_bancaria.finalizar','Finalizar conciliación bancaria','Cerrar conciliaciones bancarias cuadradas','CAJA_BANCOS','conciliacion_bancaria','finalizar'),
('conciliacion_bancaria.anular','Anular conciliación bancaria','Anular conciliaciones conservando su historial','CAJA_BANCOS','conciliacion_bancaria','anular')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,
    modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r
JOIN permisos p ON p.recurso='conciliacion_bancaria'
WHERE r.codigo='ADMINISTRADOR'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
