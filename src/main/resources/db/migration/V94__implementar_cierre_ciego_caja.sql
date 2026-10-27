CREATE TABLE cash_closure_sequence (
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    current_value BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, empresa_id),
    CONSTRAINT fk_cash_closure_sequence_empresa
        FOREIGN KEY (tenant_id, empresa_id) REFERENCES empresas(tenant_id, id)
);

CREATE TABLE cash_closures (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    closure_number VARCHAR(30) NOT NULL,
    cash_session_id UUID NOT NULL,
    cash_register_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    currency_id UUID NOT NULL,
    session_code VARCHAR(100) NOT NULL,
    cash_register_name VARCHAR(150) NOT NULL,
    branch_name VARCHAR(150) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    currency_symbol VARCHAR(10),
    opened_at TIMESTAMPTZ NOT NULL,
    opened_by UUID NOT NULL,
    opened_by_name VARCHAR(150) NOT NULL,
    closed_at TIMESTAMPTZ NOT NULL,
    closed_by UUID NOT NULL,
    closed_by_name VARCHAR(150) NOT NULL,
    cutoff_at TIMESTAMPTZ NOT NULL,
    opening_amount NUMERIC(19,4) NOT NULL,
    cash_sales_amount NUMERIC(19,4) NOT NULL,
    cash_collections_amount NUMERIC(19,4) NOT NULL,
    other_cash_in_amount NUMERIC(19,4) NOT NULL,
    cash_voucher_amount NUMERIC(19,4) NOT NULL,
    cash_refund_amount NUMERIC(19,4) NOT NULL,
    other_cash_out_amount NUMERIC(19,4) NOT NULL,
    total_cash_in_amount NUMERIC(19,4) NOT NULL,
    total_cash_out_amount NUMERIC(19,4) NOT NULL,
    expected_amount NUMERIC(19,4) NOT NULL,
    counted_amount NUMERIC(19,4) NOT NULL,
    difference_amount NUMERIC(19,4) NOT NULL,
    result VARCHAR(10) NOT NULL,
    observation VARCHAR(500),
    idempotency_key UUID NOT NULL,
    company_name VARCHAR(150) NOT NULL,
    company_tax_id VARCHAR(30),
    company_logo_object_key VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cash_closure_empresa FOREIGN KEY (tenant_id, empresa_id) REFERENCES empresas(tenant_id, id),
    CONSTRAINT fk_cash_closure_session FOREIGN KEY (tenant_id, empresa_id, cash_session_id)
        REFERENCES cash_register_session(tenant_id, empresa_id, id),
    CONSTRAINT fk_cash_closure_register FOREIGN KEY (tenant_id, empresa_id, cash_register_id)
        REFERENCES cajas(tenant_id, empresa_id, id),
    CONSTRAINT fk_cash_closure_branch FOREIGN KEY (empresa_id, branch_id) REFERENCES sucursales(empresa_id, id),
    CONSTRAINT fk_cash_closure_currency FOREIGN KEY (empresa_id, currency_id) REFERENCES monedas(empresa_id, id),
    CONSTRAINT fk_cash_closure_opened_by FOREIGN KEY (opened_by) REFERENCES usuarios(id),
    CONSTRAINT fk_cash_closure_closed_by FOREIGN KEY (closed_by) REFERENCES usuarios(id),
    CONSTRAINT ck_cash_closure_nonnegative CHECK (
        opening_amount >= 0 AND counted_amount >= 0 AND
        cash_sales_amount >= 0 AND cash_collections_amount >= 0 AND other_cash_in_amount >= 0 AND
        cash_voucher_amount >= 0 AND cash_refund_amount >= 0 AND other_cash_out_amount >= 0 AND
        total_cash_in_amount >= 0 AND total_cash_out_amount >= 0
    ),
    CONSTRAINT ck_cash_closure_result CHECK (result IN ('BALANCED','OVERAGE','SHORTAGE')),
    CONSTRAINT uk_cash_closure_session UNIQUE (tenant_id, empresa_id, cash_session_id),
    CONSTRAINT uk_cash_closure_number UNIQUE (tenant_id, empresa_id, closure_number),
    CONSTRAINT uk_cash_closure_idempotency UNIQUE (tenant_id, empresa_id, idempotency_key)
);
CREATE UNIQUE INDEX uk_cash_closure_tenant_id ON cash_closures(tenant_id, empresa_id, id);
CREATE INDEX idx_cash_closure_history ON cash_closures(tenant_id, empresa_id, closed_at DESC, id DESC);

CREATE TABLE cash_closure_details (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    cash_closure_id UUID NOT NULL,
    denomination_id UUID NOT NULL,
    denomination NUMERIC(19,4) NOT NULL,
    denomination_type VARCHAR(10) NOT NULL,
    quantity INTEGER NOT NULL,
    subtotal NUMERIC(19,4) NOT NULL,
    line_order INTEGER NOT NULL,
    CONSTRAINT fk_cash_closure_detail FOREIGN KEY (tenant_id, empresa_id, cash_closure_id)
        REFERENCES cash_closures(tenant_id, empresa_id, id),
    CONSTRAINT ck_cash_closure_detail_qty CHECK (quantity BETWEEN 0 AND 1000000),
    CONSTRAINT ck_cash_closure_detail_total CHECK (subtotal = denomination * quantity),
    CONSTRAINT uk_cash_closure_detail_denom UNIQUE (cash_closure_id, denomination_id),
    CONSTRAINT uk_cash_closure_detail_order UNIQUE (cash_closure_id, line_order)
);
CREATE INDEX idx_cash_closure_detail ON cash_closure_details(cash_closure_id, line_order);

CREATE TABLE cash_closure_payment_summary (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    cash_closure_id UUID NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    line_order INTEGER NOT NULL,
    CONSTRAINT fk_cash_closure_payment FOREIGN KEY (tenant_id, empresa_id, cash_closure_id)
        REFERENCES cash_closures(tenant_id, empresa_id, id),
    CONSTRAINT ck_cash_closure_payment_amount CHECK (amount >= 0),
    CONSTRAINT uk_cash_closure_payment_method UNIQUE (cash_closure_id, payment_method),
    CONSTRAINT uk_cash_closure_payment_order UNIQUE (cash_closure_id, line_order)
);
CREATE INDEX idx_cash_closure_payment ON cash_closure_payment_summary(cash_closure_id, line_order);

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('cierres_caja.ver','Ver cierres de caja','Consultar cierres de caja finalizados','CAJA_BANCOS','cierres_caja','ver'),
('cierres_caja.ejecutar','Ejecutar cierre de caja','Realizar el conteo ciego y cerrar un turno','CAJA_BANCOS','cierres_caja','ejecutar'),
('cierres_caja.imprimir','Imprimir cierres de caja','Generar comprobantes de cierre','CAJA_BANCOS','cierres_caja','imprimir'),
('cierres_caja.autorizar_diferencia','Autorizar diferencias de cierre','Autorizar cierres con diferencias cuando exista una política configurada','CAJA_BANCOS','cierres_caja','autorizar_diferencia')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,
modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE r.codigo='ADMINISTRADOR' AND p.recurso='cierres_caja'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
