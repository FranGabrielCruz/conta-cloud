CREATE TABLE sales_quote_sequence (
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    last_number BIGINT NOT NULL DEFAULT 0 CHECK (last_number >= 0),
    PRIMARY KEY (tenant_id,empresa_id),
    CONSTRAINT fk_sales_quote_sequence_empresa FOREIGN KEY (tenant_id,empresa_id)
        REFERENCES empresas(tenant_id,id)
);

CREATE TABLE sales_quote (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    quote_number VARCHAR(30) NOT NULL,
    customer_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    quote_date DATE NOT NULL,
    valid_until DATE NOT NULL,
    currency_id UUID NOT NULL,
    customer_name_snapshot VARCHAR(240) NOT NULL,
    customer_tax_id_snapshot VARCHAR(50),
    customer_address_snapshot VARCHAR(500),
    customer_phone_snapshot VARCHAR(30),
    customer_email_snapshot VARCHAR(180),
    reference VARCHAR(100),
    conditions VARCHAR(2000),
    subtotal_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    total_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'BORRADOR',
    sent_at TIMESTAMPTZ,
    sent_by UUID,
    accepted_at TIMESTAMPTZ,
    accepted_by UUID,
    acceptance_note VARCHAR(500),
    rejected_at TIMESTAMPTZ,
    rejected_by UUID,
    rejection_reason VARCHAR(500),
    voided_at TIMESTAMPTZ,
    voided_by UUID,
    void_reason VARCHAR(500),
    source_quote_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_sales_quote_status CHECK (status IN ('BORRADOR','ENVIADA','ACEPTADA','RECHAZADA','VENCIDA','ANULADA','CONVERTIDA')),
    CONSTRAINT ck_sales_quote_validity CHECK (valid_until >= quote_date),
    CONSTRAINT ck_sales_quote_amounts CHECK (subtotal_amount>=0 AND discount_amount>=0 AND tax_amount>=0 AND total_amount>=0),
    CONSTRAINT fk_sales_quote_empresa FOREIGN KEY (tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
    CONSTRAINT fk_sales_quote_customer FOREIGN KEY (tenant_id,empresa_id,customer_id) REFERENCES customer(tenant_id,empresa_id,id),
    CONSTRAINT fk_sales_quote_branch FOREIGN KEY (empresa_id,branch_id) REFERENCES sucursales(empresa_id,id),
    CONSTRAINT fk_sales_quote_currency FOREIGN KEY (empresa_id,currency_id) REFERENCES monedas(empresa_id,id),
    CONSTRAINT fk_sales_quote_source FOREIGN KEY (tenant_id,empresa_id,source_quote_id) REFERENCES sales_quote(tenant_id,empresa_id,id),
    CONSTRAINT fk_sales_quote_created_by FOREIGN KEY (created_by) REFERENCES usuarios(id),
    CONSTRAINT fk_sales_quote_updated_by FOREIGN KEY (updated_by) REFERENCES usuarios(id),
    CONSTRAINT fk_sales_quote_sent_by FOREIGN KEY (sent_by) REFERENCES usuarios(id),
    CONSTRAINT fk_sales_quote_accepted_by FOREIGN KEY (accepted_by) REFERENCES usuarios(id),
    CONSTRAINT fk_sales_quote_rejected_by FOREIGN KEY (rejected_by) REFERENCES usuarios(id),
    CONSTRAINT fk_sales_quote_voided_by FOREIGN KEY (voided_by) REFERENCES usuarios(id),
    CONSTRAINT uk_sales_quote_number UNIQUE (tenant_id,empresa_id,quote_number),
    CONSTRAINT uk_sales_quote_scope_id UNIQUE (tenant_id,empresa_id,id)
);

CREATE TABLE sales_quote_line (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    sales_quote_id UUID NOT NULL,
    line_number INTEGER NOT NULL CHECK (line_number > 0),
    product_id UUID NOT NULL,
    product_code_snapshot VARCHAR(24),
    description_snapshot VARCHAR(500) NOT NULL CHECK (btrim(description_snapshot)<>''),
    unit_of_measure_id_snapshot UUID,
    unit_name_snapshot VARCHAR(80),
    unit_abbreviation_snapshot VARCHAR(20),
    quantity NUMERIC(19,4) NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(19,4) NOT NULL CHECK (unit_price >= 0),
    discount_type VARCHAR(20) NOT NULL DEFAULT 'MONTO',
    discount_value NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (discount_value >= 0),
    discount_amount NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    tax_id UUID,
    tax_name_snapshot VARCHAR(100),
    tax_rate_snapshot NUMERIC(9,4) NOT NULL DEFAULT 0 CHECK (tax_rate_snapshot >= 0),
    gross_subtotal_amount NUMERIC(19,4) NOT NULL CHECK (gross_subtotal_amount >= 0),
    taxable_base_amount NUMERIC(19,4) NOT NULL CHECK (taxable_base_amount >= 0),
    tax_amount NUMERIC(19,4) NOT NULL CHECK (tax_amount >= 0),
    total_amount NUMERIC(19,4) NOT NULL CHECK (total_amount >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_sales_quote_line_discount_type CHECK (discount_type IN ('MONTO','PORCENTAJE')),
    CONSTRAINT ck_sales_quote_line_discount_value CHECK (discount_type<>'PORCENTAJE' OR discount_value<=100),
    CONSTRAINT fk_sales_quote_line_quote FOREIGN KEY (tenant_id,empresa_id,sales_quote_id)
        REFERENCES sales_quote(tenant_id,empresa_id,id) ON DELETE CASCADE,
    CONSTRAINT fk_sales_quote_line_product FOREIGN KEY (tenant_id,empresa_id,product_id)
        REFERENCES product(tenant_id,empresa_id,id),
    CONSTRAINT fk_sales_quote_line_tax FOREIGN KEY (empresa_id,tax_id) REFERENCES impuestos(empresa_id,id),
    CONSTRAINT uk_sales_quote_line_number UNIQUE (sales_quote_id,line_number)
);

CREATE TABLE sales_quote_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    sales_quote_id UUID NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    detail VARCHAR(1000),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    occurred_by UUID NOT NULL,
    CONSTRAINT fk_sales_quote_event_quote FOREIGN KEY (tenant_id,empresa_id,sales_quote_id)
        REFERENCES sales_quote(tenant_id,empresa_id,id) ON DELETE CASCADE,
    CONSTRAINT fk_sales_quote_event_user FOREIGN KEY (occurred_by) REFERENCES usuarios(id)
);

CREATE TABLE sales_quote_document_link (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    sales_quote_id UUID NOT NULL,
    document_type VARCHAR(40) NOT NULL,
    document_id UUID NOT NULL,
    document_number VARCHAR(40) NOT NULL,
    document_status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    CONSTRAINT fk_sales_quote_document_quote FOREIGN KEY (tenant_id,empresa_id,sales_quote_id)
        REFERENCES sales_quote(tenant_id,empresa_id,id),
    CONSTRAINT fk_sales_quote_document_user FOREIGN KEY (created_by) REFERENCES usuarios(id),
    CONSTRAINT uk_sales_quote_document UNIQUE (tenant_id,empresa_id,document_type,document_id)
);

CREATE TABLE sales_quote_line_conversion (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    sales_quote_line_id UUID NOT NULL,
    document_link_id UUID NOT NULL,
    converted_quantity NUMERIC(19,4) NOT NULL CHECK (converted_quantity > 0),
    CONSTRAINT fk_sales_quote_conversion_line FOREIGN KEY (sales_quote_line_id) REFERENCES sales_quote_line(id),
    CONSTRAINT fk_sales_quote_conversion_document FOREIGN KEY (document_link_id) REFERENCES sales_quote_document_link(id),
    CONSTRAINT uk_sales_quote_line_document UNIQUE (sales_quote_line_id,document_link_id)
);

CREATE INDEX idx_sales_quote_list ON sales_quote(tenant_id,empresa_id,quote_date DESC,created_at DESC);
CREATE INDEX idx_sales_quote_status ON sales_quote(tenant_id,empresa_id,status,valid_until);
CREATE INDEX idx_sales_quote_customer ON sales_quote(tenant_id,empresa_id,customer_id,quote_date DESC);
CREATE INDEX idx_sales_quote_currency ON sales_quote(tenant_id,empresa_id,currency_id,quote_date DESC);
CREATE INDEX idx_sales_quote_line_quote ON sales_quote_line(tenant_id,empresa_id,sales_quote_id);
CREATE INDEX idx_sales_quote_line_product ON sales_quote_line(tenant_id,empresa_id,product_id);
CREATE INDEX idx_sales_quote_event_quote ON sales_quote_event(tenant_id,empresa_id,sales_quote_id,occurred_at);
CREATE INDEX idx_sales_quote_document_quote ON sales_quote_document_link(tenant_id,empresa_id,sales_quote_id,created_at);

CREATE TRIGGER trg_sales_quote_tenant_empresa BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON sales_quote
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();
CREATE TRIGGER trg_sales_quote_line_tenant_empresa BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON sales_quote_line
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();
CREATE TRIGGER trg_sales_quote_event_tenant_empresa BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON sales_quote_event
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();
CREATE TRIGGER trg_sales_quote_document_tenant_empresa BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON sales_quote_document_link
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();
CREATE TRIGGER trg_sales_quote_conversion_tenant_empresa BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON sales_quote_line_conversion
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('cotizaciones.ver','Ver cotizaciones','Consultar cotizaciones','VENTAS','cotizaciones','ver'),
('cotizaciones.crear','Crear cotizaciones','Crear cotizaciones en borrador','VENTAS','cotizaciones','crear'),
('cotizaciones.editar','Editar cotizaciones','Modificar cotizaciones en borrador','VENTAS','cotizaciones','editar'),
('cotizaciones.precios','Modificar precios de cotizaciones','Modificar precios de líneas','VENTAS','cotizaciones','precios'),
('cotizaciones.descuentos','Modificar descuentos de cotizaciones','Modificar descuentos de líneas','VENTAS','cotizaciones','descuentos'),
('cotizaciones.enviar','Enviar cotizaciones','Marcar cotizaciones como enviadas','VENTAS','cotizaciones','enviar'),
('cotizaciones.aceptar','Aceptar cotizaciones','Marcar cotizaciones como aceptadas','VENTAS','cotizaciones','aceptar'),
('cotizaciones.rechazar','Rechazar cotizaciones','Marcar cotizaciones como rechazadas','VENTAS','cotizaciones','rechazar'),
('cotizaciones.duplicar','Duplicar cotizaciones','Duplicar o renovar cotizaciones','VENTAS','cotizaciones','duplicar'),
('cotizaciones.convertir','Convertir cotizaciones','Preparar facturas desde cotizaciones aceptadas','VENTAS','cotizaciones','convertir'),
('cotizaciones.anular','Anular cotizaciones','Anular cotizaciones','VENTAS','cotizaciones','anular'),
('cotizaciones.imprimir','Imprimir cotizaciones','Generar y reimprimir el PDF de cotizaciones','VENTAS','cotizaciones','imprimir')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,
    modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r JOIN permisos p ON p.recurso='cotizaciones'
WHERE r.codigo='ADMINISTRADOR'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;

INSERT INTO empresa_modulos(tenant_id,empresa_id,module_key,enabled)
SELECT e.tenant_id,e.id,'VENTAS',TRUE FROM empresas e WHERE upper(e.codigo)='DEMO'
ON CONFLICT(empresa_id,module_key) DO UPDATE SET enabled=TRUE,updated_at=CURRENT_TIMESTAMP;
