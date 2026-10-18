CREATE TABLE sales_payment_method (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL, empresa_id UUID NOT NULL,
    code VARCHAR(30) NOT NULL, name VARCHAR(100) NOT NULL, type VARCHAR(24) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE, display_order INTEGER NOT NULL DEFAULT 0,
    requires_reference BOOLEAN NOT NULL DEFAULT FALSE, requires_bank_account BOOLEAN NOT NULL DEFAULT FALSE,
    allows_change BOOLEAN NOT NULL DEFAULT FALSE, is_cash BOOLEAN NOT NULL DEFAULT FALSE,
    is_electronic BOOLEAN NOT NULL DEFAULT FALSE, icon VARCHAR(40), is_default BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_sales_payment_method_type CHECK(type IN('CASH','CREDIT_CARD','DEBIT_CARD','BANK_TRANSFER','CHECK','OTHER')),
    CONSTRAINT fk_sales_payment_method_empresa FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
    CONSTRAINT uk_sales_payment_method_code UNIQUE(tenant_id,empresa_id,code),
    CONSTRAINT uk_sales_payment_method_scope UNIQUE(tenant_id,empresa_id,id)
);
CREATE INDEX idx_sales_payment_method_list ON sales_payment_method(tenant_id,empresa_id,active,display_order);
CREATE UNIQUE INDEX uk_sales_payment_method_default ON sales_payment_method(tenant_id,empresa_id) WHERE is_default AND active;
CREATE TRIGGER trg_sales_payment_method_tenant_empresa BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON sales_payment_method
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();

INSERT INTO sales_payment_method(tenant_id,empresa_id,code,name,type,display_order,requires_reference,requires_bank_account,allows_change,is_cash,is_electronic,icon,is_default)
SELECT e.tenant_id,e.id,v.code,v.name,v.type,v.orden,v.reference,v.account,v.change,v.cash,v.electronic,v.icon,v.default_method
FROM empresas e CROSS JOIN (VALUES
 ('CASH','Efectivo','CASH',10,FALSE,FALSE,TRUE,TRUE,FALSE,'CASH',TRUE),
 ('CREDIT_CARD','Tarjeta de crédito','CREDIT_CARD',20,FALSE,FALSE,FALSE,FALSE,TRUE,'CREDIT_CARD',FALSE),
 ('DEBIT_CARD','Tarjeta de débito','DEBIT_CARD',30,FALSE,FALSE,FALSE,FALSE,TRUE,'DEBIT_CARD',FALSE),
 ('BANK_TRANSFER','Transferencia bancaria','BANK_TRANSFER',40,FALSE,TRUE,FALSE,FALSE,TRUE,'BANK',FALSE),
 ('CHECK','Cheque','CHECK',50,TRUE,FALSE,FALSE,FALSE,FALSE,'CHECK',FALSE)
) AS v(code,name,type,orden,reference,account,change,cash,electronic,icon,default_method)
ON CONFLICT(tenant_id,empresa_id,code) DO NOTHING;

ALTER TABLE sales_payment DROP CONSTRAINT ck_sales_payment_method;
ALTER TABLE sales_payment DROP CONSTRAINT ck_sales_payment_amounts;
ALTER TABLE sales_payment RENAME COLUMN amount TO total_amount;
ALTER TABLE sales_payment RENAME COLUMN received_amount TO legacy_received_amount;
ALTER TABLE sales_payment ADD COLUMN applied_amount NUMERIC(19,4);
ALTER TABLE sales_payment ADD COLUMN status VARCHAR(20);
ALTER TABLE sales_payment ADD COLUMN confirmed_at TIMESTAMPTZ;
ALTER TABLE sales_payment ADD COLUMN confirmed_by UUID;
UPDATE sales_payment SET applied_amount=total_amount,status='CONFIRMED',confirmed_at=created_at,confirmed_by=created_by;
ALTER TABLE sales_payment ALTER COLUMN applied_amount SET NOT NULL;
ALTER TABLE sales_payment ALTER COLUMN status SET NOT NULL;
ALTER TABLE sales_payment ALTER COLUMN confirmed_at SET NOT NULL;
ALTER TABLE sales_payment ALTER COLUMN confirmed_by SET NOT NULL;
ALTER TABLE sales_payment DROP COLUMN payment_method;
ALTER TABLE sales_payment ADD CONSTRAINT ck_sales_payment_status CHECK(status IN('CONFIRMED','VOIDED'));
ALTER TABLE sales_payment ADD CONSTRAINT ck_sales_payment_amounts CHECK(total_amount>0 AND applied_amount=total_amount AND change_amount>=0);
ALTER TABLE sales_payment ADD CONSTRAINT fk_sales_payment_confirmed_by FOREIGN KEY(confirmed_by) REFERENCES usuarios(id);
ALTER TABLE sales_payment ADD CONSTRAINT uk_sales_payment_scope UNIQUE(tenant_id,empresa_id,id);

CREATE TABLE sales_payment_application (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL, empresa_id UUID NOT NULL,
    sales_payment_id UUID NOT NULL, payment_method_id UUID NOT NULL,
    method_code VARCHAR(30) NOT NULL, method_name VARCHAR(100) NOT NULL, method_type VARCHAR(24) NOT NULL,
    amount NUMERIC(19,4) NOT NULL, received_amount NUMERIC(19,4), change_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    bank_account_id UUID, reference VARCHAR(100), bank_name VARCHAR(120), line_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_sales_payment_application_type CHECK(method_type IN('CASH','CREDIT_CARD','DEBIT_CARD','BANK_TRANSFER','CHECK','OTHER')),
    CONSTRAINT ck_sales_payment_application_amounts CHECK(amount>0 AND change_amount>=0 AND (received_amount IS NULL OR received_amount>=amount)),
    CONSTRAINT fk_sales_payment_application_payment FOREIGN KEY(tenant_id,empresa_id,sales_payment_id) REFERENCES sales_payment(tenant_id,empresa_id,id),
    CONSTRAINT fk_sales_payment_application_method FOREIGN KEY(tenant_id,empresa_id,payment_method_id) REFERENCES sales_payment_method(tenant_id,empresa_id,id),
    CONSTRAINT fk_sales_payment_application_bank FOREIGN KEY(tenant_id,empresa_id,bank_account_id) REFERENCES cuentas_bancarias(tenant_id,empresa_id,id),
    CONSTRAINT uk_sales_payment_application_order UNIQUE(sales_payment_id,line_order)
);
CREATE INDEX idx_sales_payment_application_payment ON sales_payment_application(tenant_id,empresa_id,sales_payment_id,line_order);
CREATE TRIGGER trg_sales_payment_application_tenant_empresa BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON sales_payment_application
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();

INSERT INTO sales_payment_application(tenant_id,empresa_id,sales_payment_id,payment_method_id,method_code,method_name,method_type,
 amount,received_amount,change_amount,line_order)
SELECT p.tenant_id,p.empresa_id,p.id,m.id,m.code,m.name,m.type,p.total_amount,p.legacy_received_amount,p.change_amount,1
FROM sales_payment p JOIN sales_payment_method m ON m.tenant_id=p.tenant_id AND m.empresa_id=p.empresa_id AND m.code='CASH';
ALTER TABLE sales_payment DROP COLUMN legacy_received_amount;

ALTER TABLE movimientos_financieros DROP CONSTRAINT ck_movimientos_financieros_origen;
ALTER TABLE movimientos_financieros ADD CONSTRAINT ck_movimientos_financieros_origen CHECK (tipo_origen IN
 ('MANUAL','CUSTOMER_RECEIPT','SUPPLIER_PAYMENT','SALES_INVOICE','SALES_PAYMENT','TRANSFER','OPENING_BALANCE','ADJUSTMENT','OTHER'));

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('facturas.cobrar_transferencia','Cobrar ventas por transferencia','Aplicar cobros de venta a cuentas bancarias','VENTAS','facturas','cobrar_transferencia'),
('facturas.cobrar_cheque','Cobrar ventas con cheque','Aplicar cobros de venta mediante cheque','VENTAS','facturas','cobrar_cheque'),
('facturas.anular_cobro','Anular cobros de venta','Revertir cobros confirmados mediante un flujo trazable','VENTAS','facturas','anular_cobro')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;
INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE upper(r.codigo)='ADMINISTRADOR' AND p.codigo IN('facturas.cobrar_transferencia','facturas.cobrar_cheque','facturas.anular_cobro')
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
