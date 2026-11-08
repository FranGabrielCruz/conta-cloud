CREATE TABLE sales_debit_note_reason (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL, empresa_id UUID NOT NULL,
 code VARCHAR(40) NOT NULL, name VARCHAR(150) NOT NULL, active BOOLEAN NOT NULL DEFAULT true,
 UNIQUE(tenant_id,empresa_id,id), UNIQUE(tenant_id,empresa_id,code),
 FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id));
CREATE TRIGGER trg_debit_reason_scope BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON sales_debit_note_reason
 FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();
INSERT INTO sales_debit_note_reason(tenant_id,empresa_id,code,name)
SELECT e.tenant_id,e.id,v.code,v.name FROM empresas e CROSS JOIN (VALUES
 ('PRICE','Diferencia de precio'),('CHARGE','Cargo adicional'),('EXPENSE','Gastos adicionales'),
 ('INTEREST','Intereses / Mora'),('CORRECTION','Corrección de facturación'),('OTHER','Otro')) v(code,name);
CREATE TABLE sales_debit_note_sequence (
 tenant_id UUID NOT NULL,empresa_id UUID NOT NULL,last_number BIGINT NOT NULL DEFAULT 0 CHECK(last_number>=0),
 PRIMARY KEY(tenant_id,empresa_id),FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id));
CREATE TABLE sales_debit_note (
 id UUID PRIMARY KEY,tenant_id UUID NOT NULL,empresa_id UUID NOT NULL,
 debit_note_number VARCHAR(50) NOT NULL,sales_invoice_id UUID NOT NULL,customer_id UUID NOT NULL,
 currency_id UUID NOT NULL,reason_id UUID NOT NULL,issue_date DATE NOT NULL,
 amount NUMERIC(19,4) NOT NULL CHECK(amount>0),document_snapshot TEXT NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'CONFIRMED' CHECK(status IN('CONFIRMED','VOIDED')),
 confirmed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,confirmed_by UUID NOT NULL REFERENCES usuarios(id),
 version BIGINT NOT NULL DEFAULT 0,annulled_at TIMESTAMPTZ,annulled_by UUID REFERENCES usuarios(id),
 annulled_by_name VARCHAR(200),annulment_reason VARCHAR(1000),annulment_key UUID,annulled_zone VARCHAR(80),
 UNIQUE(tenant_id,empresa_id,id),UNIQUE(tenant_id,empresa_id,debit_note_number),
 FOREIGN KEY(tenant_id,empresa_id,sales_invoice_id) REFERENCES sales_invoice(tenant_id,empresa_id,id),
 FOREIGN KEY(tenant_id,empresa_id,customer_id) REFERENCES customer(tenant_id,empresa_id,id),
 FOREIGN KEY(empresa_id,currency_id) REFERENCES monedas(empresa_id,id),
 FOREIGN KEY(tenant_id,empresa_id,reason_id) REFERENCES sales_debit_note_reason(tenant_id,empresa_id,id),
 CHECK((status='CONFIRMED' AND annulled_at IS NULL AND annulment_reason IS NULL AND annulled_by IS NULL AND annulment_key IS NULL)
 OR (status='VOIDED' AND annulled_at IS NOT NULL AND annulled_by IS NOT NULL AND annulled_by_name IS NOT NULL AND length(trim(annulment_reason)) BETWEEN 3 AND 1000 AND annulment_key IS NOT NULL AND annulled_zone IS NOT NULL)));
CREATE TRIGGER trg_debit_note_scope BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON sales_debit_note
 FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();
CREATE INDEX idx_debit_invoice ON sales_debit_note(tenant_id,empresa_id,sales_invoice_id,status);
CREATE INDEX idx_debit_list ON sales_debit_note(tenant_id,empresa_id,confirmed_at DESC,id);
CREATE TABLE sales_debit_note_line (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),tenant_id UUID NOT NULL,empresa_id UUID NOT NULL,
 sales_debit_note_id UUID NOT NULL,line_order INTEGER NOT NULL CHECK(line_order>0),description VARCHAR(500) NOT NULL,
 quantity NUMERIC(19,4) NOT NULL CHECK(quantity>0),unit_price NUMERIC(19,4) NOT NULL CHECK(unit_price>=0),
 gross_amount NUMERIC(19,4) NOT NULL CHECK(gross_amount>=0),discount_amount NUMERIC(19,4) NOT NULL CHECK(discount_amount>=0 AND discount_amount<=gross_amount),
 tax_id UUID,tax_name VARCHAR(150),tax_rate NUMERIC(9,6) NOT NULL CHECK(tax_rate>=0),tax_amount NUMERIC(19,4) NOT NULL CHECK(tax_amount>=0),
 total NUMERIC(19,4) NOT NULL CHECK(total=gross_amount-discount_amount+tax_amount),
 UNIQUE(tenant_id,empresa_id,sales_debit_note_id,line_order),
 FOREIGN KEY(tenant_id,empresa_id,sales_debit_note_id) REFERENCES sales_debit_note(tenant_id,empresa_id,id),
 FOREIGN KEY(empresa_id,tax_id) REFERENCES impuestos(empresa_id,id));
CREATE TRIGGER trg_debit_line_scope BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON sales_debit_note_line
 FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();
INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion)
SELECT 'notas_debito.'||v.accion,v.nombre,v.nombre,'VENTAS','notas_debito',v.accion FROM (VALUES
 ('ver','Ver notas de débito'),('crear','Crear notas de débito'),('emitir','Emitir notas de débito'),
 ('pdf','Imprimir notas de débito'),('anular','Anular notas de débito')) v(accion,nombre) ON CONFLICT(codigo) DO NOTHING;
INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p WHERE upper(r.codigo)='ADMINISTRADOR' AND p.codigo LIKE 'notas_debito.%'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
