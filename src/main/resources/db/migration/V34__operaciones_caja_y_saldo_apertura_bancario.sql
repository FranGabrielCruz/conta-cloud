CREATE TABLE cash_register_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    cash_register_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    currency_id UUID NOT NULL,
    business_date DATE NOT NULL,
    shift_number INTEGER NOT NULL,
    display_code VARCHAR(100) NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    opened_by UUID NOT NULL,
    opening_amount NUMERIC(19,4) NOT NULL,
    opening_note VARCHAR(500),
    closed_at TIMESTAMPTZ,
    closed_by UUID,
    expected_cash_amount NUMERIC(19,4),
    counted_cash_amount NUMERIC(19,4),
    difference_amount NUMERIC(19,4),
    closing_note VARCHAR(500),
    status VARCHAR(12) NOT NULL DEFAULT 'OPEN',
    review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_at TIMESTAMPTZ,
    reviewed_by UUID,
    review_note VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_cash_session_shift CHECK (shift_number > 0),
    CONSTRAINT ck_cash_session_opening CHECK (opening_amount >= 0),
    CONSTRAINT ck_cash_session_counted CHECK (counted_cash_amount IS NULL OR counted_cash_amount >= 0),
    CONSTRAINT ck_cash_session_status CHECK (status IN ('OPEN','CLOSED')),
    CONSTRAINT ck_cash_session_review CHECK (review_status IN ('PENDING','APPROVED','REQUIRES_REVIEW')),
    CONSTRAINT ck_cash_session_lifecycle CHECK (
        (status='OPEN' AND closed_at IS NULL AND closed_by IS NULL AND expected_cash_amount IS NULL
            AND counted_cash_amount IS NULL AND difference_amount IS NULL)
        OR (status='CLOSED' AND closed_at IS NOT NULL AND closed_by IS NOT NULL
            AND expected_cash_amount IS NOT NULL AND counted_cash_amount IS NOT NULL AND difference_amount IS NOT NULL)),
    CONSTRAINT fk_cash_session_tenant_empresa FOREIGN KEY (tenant_id,empresa_id)
        REFERENCES empresas(tenant_id,id),
    CONSTRAINT fk_cash_session_register FOREIGN KEY (tenant_id,empresa_id,cash_register_id)
        REFERENCES cajas(tenant_id,empresa_id,id),
    CONSTRAINT fk_cash_session_branch FOREIGN KEY (empresa_id,branch_id) REFERENCES sucursales(empresa_id,id),
    CONSTRAINT fk_cash_session_currency FOREIGN KEY (empresa_id,currency_id) REFERENCES monedas(empresa_id,id),
    CONSTRAINT fk_cash_session_opened_by FOREIGN KEY (opened_by) REFERENCES usuarios(id),
    CONSTRAINT fk_cash_session_closed_by FOREIGN KEY (closed_by) REFERENCES usuarios(id),
    CONSTRAINT fk_cash_session_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES usuarios(id),
    CONSTRAINT uk_cash_session_shift UNIQUE (tenant_id,empresa_id,cash_register_id,business_date,shift_number),
    CONSTRAINT uk_cash_session_display UNIQUE (tenant_id,empresa_id,display_code)
);

CREATE UNIQUE INDEX uk_cash_session_tenant_empresa_id
    ON cash_register_session(tenant_id,empresa_id,id);
CREATE UNIQUE INDEX uk_cash_session_open_register
    ON cash_register_session(tenant_id,empresa_id,cash_register_id) WHERE status='OPEN';
CREATE UNIQUE INDEX uk_cash_session_open_user
    ON cash_register_session(tenant_id,empresa_id,opened_by) WHERE status='OPEN';
CREATE INDEX idx_cash_session_search
    ON cash_register_session(tenant_id,empresa_id,business_date DESC,status,review_status);
CREATE INDEX idx_cash_session_branch
    ON cash_register_session(tenant_id,empresa_id,branch_id);

CREATE TRIGGER trg_cash_session_tenant_empresa
BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON cash_register_session
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();

ALTER TABLE movimientos_financieros
    ADD COLUMN cash_register_session_id UUID,
    ADD COLUMN payment_method VARCHAR(20);

UPDATE movimientos_financieros
SET payment_method=CASE WHEN tipo_cuenta='CASH_REGISTER' THEN 'CASH' ELSE 'BANK_TRANSFER' END;

ALTER TABLE movimientos_financieros
    ALTER COLUMN payment_method SET NOT NULL,
    ADD CONSTRAINT ck_financial_movement_payment_method CHECK
        (payment_method IN ('CASH','CARD','BANK_TRANSFER','CHECK','CREDIT','WALLET','OTHER')),
    ADD CONSTRAINT fk_financial_movement_cash_session
        FOREIGN KEY (tenant_id,empresa_id,cash_register_session_id)
        REFERENCES cash_register_session(tenant_id,empresa_id,id);

CREATE INDEX idx_financial_movement_cash_session
    ON movimientos_financieros(tenant_id,empresa_id,cash_register_session_id,estado,creado_en);

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('operaciones_caja.ver','Ver operaciones de caja','Consultar estado operativo de las cajas','CAJA_BANCOS','operaciones_caja','ver'),
('operaciones_caja.abrir','Abrir caja','Abrir turnos de caja y registrar fondo inicial','CAJA_BANCOS','operaciones_caja','abrir'),
('operaciones_caja.cerrar','Cerrar caja','Cerrar turnos mediante arqueo de efectivo','CAJA_BANCOS','operaciones_caja','cerrar'),
('operaciones_caja.ver_historial','Ver historial de caja','Consultar turnos y cierres históricos','CAJA_BANCOS','operaciones_caja','ver_historial'),
('operaciones_caja.revisar_cierre','Revisar cierres de caja','Aprobar o marcar cierres para revisión','CAJA_BANCOS','operaciones_caja','revisar_cierre')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,
    modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r JOIN permisos p ON p.recurso='operaciones_caja'
WHERE r.codigo='ADMINISTRADOR'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;

INSERT INTO empresa_modulos(tenant_id,empresa_id,module_key,enabled)
SELECT e.tenant_id,e.id,'CAJA_BANCOS',TRUE FROM empresas e WHERE upper(e.codigo)='DEMO'
ON CONFLICT(empresa_id,module_key) DO UPDATE SET enabled=TRUE,updated_at=CURRENT_TIMESTAMP;
