CREATE TABLE cash_denominations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL, empresa_id UUID NOT NULL,
    currency_id UUID NOT NULL, denomination NUMERIC(19,4) NOT NULL, denomination_type VARCHAR(10) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0, active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_cash_denom_empresa FOREIGN KEY (tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
    CONSTRAINT fk_cash_denom_currency FOREIGN KEY (empresa_id,currency_id) REFERENCES monedas(empresa_id,id),
    CONSTRAINT ck_cash_denom_value CHECK (denomination > 0),
    CONSTRAINT ck_cash_denom_type CHECK (denomination_type IN ('BANKNOTE','COIN')),
    CONSTRAINT uk_cash_denom UNIQUE (tenant_id,empresa_id,currency_id,denomination)
);
CREATE INDEX idx_cash_denom_currency ON cash_denominations(tenant_id,empresa_id,currency_id,active,display_order);

INSERT INTO cash_denominations(tenant_id,empresa_id,currency_id,denomination,denomination_type,display_order)
SELECT e.tenant_id,m.empresa_id,m.id,v.amount,v.kind,v.ord FROM monedas m
JOIN empresas e ON e.id=m.empresa_id
CROSS JOIN (VALUES (2000::numeric,'BANKNOTE',1),(1000,'BANKNOTE',2),(500,'BANKNOTE',3),
 (200,'BANKNOTE',4),(100,'BANKNOTE',5),(50,'BANKNOTE',6),(25,'COIN',7),(10,'COIN',8),
 (5,'COIN',9),(1,'COIN',10)) v(amount,kind,ord)
WHERE upper(m.codigo_iso)='DOP' ON CONFLICT DO NOTHING;

CREATE TABLE cash_count_sequence (
    tenant_id UUID NOT NULL, empresa_id UUID NOT NULL, current_value BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY(tenant_id,empresa_id),
    CONSTRAINT fk_cash_count_sequence_empresa FOREIGN KEY (tenant_id,empresa_id) REFERENCES empresas(tenant_id,id)
);

CREATE TABLE cash_counts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL, empresa_id UUID NOT NULL,
    count_number VARCHAR(30) NOT NULL, cash_register_id UUID NOT NULL, cash_session_id UUID NOT NULL,
    branch_id UUID NOT NULL, currency_id UUID NOT NULL, counted_at TIMESTAMPTZ NOT NULL,
    snapshot_at TIMESTAMPTZ NOT NULL, counted_by UUID NOT NULL,
    expected_amount NUMERIC(19,4) NOT NULL, counted_amount NUMERIC(19,4) NOT NULL,
    difference_amount NUMERIC(19,4) NOT NULL, result VARCHAR(10) NOT NULL,
    observation VARCHAR(500), idempotency_key UUID NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cash_count_empresa FOREIGN KEY (tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
    CONSTRAINT fk_cash_count_register FOREIGN KEY (tenant_id,empresa_id,cash_register_id) REFERENCES cajas(tenant_id,empresa_id,id),
    CONSTRAINT fk_cash_count_session FOREIGN KEY (tenant_id,empresa_id,cash_session_id) REFERENCES cash_register_session(tenant_id,empresa_id,id),
    CONSTRAINT fk_cash_count_branch FOREIGN KEY (empresa_id,branch_id) REFERENCES sucursales(empresa_id,id),
    CONSTRAINT fk_cash_count_currency FOREIGN KEY (empresa_id,currency_id) REFERENCES monedas(empresa_id,id),
    CONSTRAINT fk_cash_count_user FOREIGN KEY (counted_by) REFERENCES usuarios(id),
    CONSTRAINT ck_cash_count_values CHECK (expected_amount>=0 AND counted_amount>=0),
    CONSTRAINT ck_cash_count_result CHECK (result IN ('BALANCED','OVERAGE','SHORTAGE')),
    CONSTRAINT uk_cash_count_number UNIQUE(tenant_id,empresa_id,count_number),
    CONSTRAINT uk_cash_count_idempotency UNIQUE(tenant_id,empresa_id,idempotency_key)
);
CREATE UNIQUE INDEX uk_cash_count_tenant_id ON cash_counts(tenant_id,empresa_id,id);
CREATE INDEX idx_cash_count_session ON cash_counts(tenant_id,empresa_id,cash_session_id,counted_at DESC,id DESC);

CREATE TABLE cash_count_details (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL, empresa_id UUID NOT NULL,
    cash_count_id UUID NOT NULL, denomination_id UUID NOT NULL, denomination NUMERIC(19,4) NOT NULL,
    denomination_type VARCHAR(10) NOT NULL, quantity INTEGER NOT NULL, subtotal NUMERIC(19,4) NOT NULL,
    line_order INTEGER NOT NULL,
    CONSTRAINT fk_cash_count_detail_count FOREIGN KEY (tenant_id,empresa_id,cash_count_id) REFERENCES cash_counts(tenant_id,empresa_id,id),
    CONSTRAINT fk_cash_count_detail_denom FOREIGN KEY (denomination_id) REFERENCES cash_denominations(id),
    CONSTRAINT ck_cash_count_detail_qty CHECK (quantity>=0 AND quantity<=1000000),
    CONSTRAINT ck_cash_count_detail_total CHECK (subtotal=denomination*quantity),
    CONSTRAINT uk_cash_count_detail UNIQUE(cash_count_id,denomination_id),
    CONSTRAINT uk_cash_count_detail_order UNIQUE(cash_count_id,line_order)
);
CREATE INDEX idx_cash_count_detail_count ON cash_count_details(cash_count_id,line_order);

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('recuentos_caja.crear','Realizar recuentos de caja','Registrar conteos físicos de efectivo','VENTAS','recuentos_caja','crear'),
('recuentos_caja.ver','Ver recuentos de caja','Consultar recuentos históricos','VENTAS','recuentos_caja','ver'),
('recuentos_caja.imprimir','Imprimir recuentos de caja','Generar comprobantes de recuento','VENTAS','recuentos_caja','imprimir')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;
INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE r.codigo='ADMINISTRADOR' AND p.recurso='recuentos_caja'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
