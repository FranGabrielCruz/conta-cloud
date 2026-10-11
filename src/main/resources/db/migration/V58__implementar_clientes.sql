CREATE UNIQUE INDEX IF NOT EXISTS uk_condiciones_pago_empresa_id
    ON condiciones_pago(empresa_id,id);

CREATE TABLE customer_sequence (
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    last_number BIGINT NOT NULL DEFAULT 0 CHECK (last_number >= 0),
    PRIMARY KEY (tenant_id,empresa_id),
    CONSTRAINT fk_customer_sequence_empresa FOREIGN KEY (tenant_id,empresa_id)
        REFERENCES empresas(tenant_id,id)
);

CREATE TABLE customer (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    code VARCHAR(24) NOT NULL,
    person_type VARCHAR(20) NOT NULL,
    first_names VARCHAR(120),
    last_names VARCHAR(120),
    legal_name VARCHAR(180),
    trade_name VARCHAR(150),
    identification_type VARCHAR(30),
    identification VARCHAR(50),
    identification_normalized VARCHAR(50),
    phone VARCHAR(30),
    alternate_phone VARCHAR(30),
    email VARCHAR(180),
    payment_term_id UUID NOT NULL,
    currency_id UUID NOT NULL,
    credit_days INTEGER NOT NULL DEFAULT 0 CHECK (credit_days >= 0),
    credit_limit NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (credit_limit >= 0),
    credit_limit_control VARCHAR(20) NOT NULL DEFAULT 'NO_CONTROLAR',
    preferred_receipt_type_id UUID,
    tax_treatment VARCHAR(100),
    notes VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_customer_person_type CHECK (person_type IN ('FISICA','JURIDICA')),
    CONSTRAINT ck_customer_name CHECK (
        (person_type='FISICA' AND btrim(coalesce(first_names,''))<>'' AND btrim(coalesce(last_names,''))<>'') OR
        (person_type='JURIDICA' AND btrim(coalesce(legal_name,''))<>'')),
    CONSTRAINT ck_customer_identification_type CHECK (identification_type IS NULL OR identification_type IN
        ('RNC','CEDULA','PASAPORTE','IDENTIFICACION_EXTRANJERA','OTRO')),
    CONSTRAINT ck_customer_credit_control CHECK (credit_limit_control IN ('ADVERTIR','BLOQUEAR','NO_CONTROLAR')),
    CONSTRAINT fk_customer_empresa FOREIGN KEY (tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
    CONSTRAINT fk_customer_payment_term FOREIGN KEY (empresa_id,payment_term_id) REFERENCES condiciones_pago(empresa_id,id),
    CONSTRAINT fk_customer_currency FOREIGN KEY (empresa_id,currency_id) REFERENCES monedas(empresa_id,id),
    CONSTRAINT fk_customer_receipt_type FOREIGN KEY (empresa_id,preferred_receipt_type_id)
        REFERENCES tipos_comprobantes_fiscales(empresa_id,id),
    CONSTRAINT fk_customer_created_by FOREIGN KEY (created_by) REFERENCES usuarios(id),
    CONSTRAINT fk_customer_updated_by FOREIGN KEY (updated_by) REFERENCES usuarios(id),
    CONSTRAINT uk_customer_code UNIQUE (tenant_id,empresa_id,code)
);

CREATE UNIQUE INDEX uk_customer_tenant_empresa_id ON customer(tenant_id,empresa_id,id);
CREATE UNIQUE INDEX uk_customer_identification ON customer(tenant_id,empresa_id,identification_type,identification_normalized)
    WHERE identification_normalized IS NOT NULL;
CREATE INDEX idx_customer_list ON customer(tenant_id,empresa_id,active,legal_name,first_names,last_names);
CREATE INDEX idx_customer_search ON customer(tenant_id,empresa_id,trade_name,email,phone);

CREATE TABLE customer_address (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL, empresa_id UUID NOT NULL,
    customer_id UUID NOT NULL, address_type VARCHAR(20) NOT NULL, country VARCHAR(100) NOT NULL,
    province VARCHAR(100), city VARCHAR(100), sector VARCHAR(100), address VARCHAR(500) NOT NULL,
    postal_code VARCHAR(20), principal BOOLEAN NOT NULL DEFAULT FALSE, active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_by UUID NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_customer_address_type CHECK (address_type IN ('FISCAL','FACTURACION','ENTREGA','COMERCIAL','OTRA')),
    CONSTRAINT fk_customer_address_customer FOREIGN KEY (tenant_id,empresa_id,customer_id)
        REFERENCES customer(tenant_id,empresa_id,id),
    CONSTRAINT fk_customer_address_created_by FOREIGN KEY (created_by) REFERENCES usuarios(id),
    CONSTRAINT fk_customer_address_updated_by FOREIGN KEY (updated_by) REFERENCES usuarios(id)
);
CREATE INDEX idx_customer_address_customer ON customer_address(tenant_id,empresa_id,customer_id,active);
CREATE UNIQUE INDEX uk_customer_address_principal ON customer_address(tenant_id,empresa_id,customer_id)
    WHERE principal AND active;

CREATE TABLE customer_contact (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL, empresa_id UUID NOT NULL,
    customer_id UUID NOT NULL, name VARCHAR(150) NOT NULL, position VARCHAR(100), phone VARCHAR(30),
    email VARCHAR(180), principal BOOLEAN NOT NULL DEFAULT FALSE, active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_by UUID NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_customer_contact_customer FOREIGN KEY (tenant_id,empresa_id,customer_id)
        REFERENCES customer(tenant_id,empresa_id,id),
    CONSTRAINT fk_customer_contact_created_by FOREIGN KEY (created_by) REFERENCES usuarios(id),
    CONSTRAINT fk_customer_contact_updated_by FOREIGN KEY (updated_by) REFERENCES usuarios(id)
);
CREATE INDEX idx_customer_contact_customer ON customer_contact(tenant_id,empresa_id,customer_id,active);

CREATE TRIGGER trg_customer_tenant_empresa BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON customer
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();
CREATE TRIGGER trg_customer_address_tenant_empresa BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON customer_address
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();
CREATE TRIGGER trg_customer_contact_tenant_empresa BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON customer_contact
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('clientes.ver','Ver clientes','Consultar clientes','VENTAS','clientes','ver'),
('clientes.crear','Crear clientes','Crear clientes','VENTAS','clientes','crear'),
('clientes.editar','Editar clientes','Modificar clientes','VENTAS','clientes','editar'),
('clientes.activar','Activar clientes','Activar clientes','VENTAS','clientes','activar'),
('clientes.desactivar','Desactivar clientes','Desactivar clientes','VENTAS','clientes','desactivar'),
('clientes.credito.ver','Ver condiciones de crédito','Consultar condiciones de crédito de clientes','VENTAS','clientes','credito_ver'),
('clientes.credito.editar','Editar condiciones de crédito','Modificar condiciones de crédito de clientes','VENTAS','clientes','credito_editar'),
('clientes.contactos.ver','Ver contactos de clientes','Consultar contactos de clientes','VENTAS','clientes','contactos_ver'),
('clientes.contactos.administrar','Administrar contactos de clientes','Crear y modificar contactos de clientes','VENTAS','clientes','contactos_administrar'),
('clientes.direcciones.ver','Ver direcciones de clientes','Consultar direcciones de clientes','VENTAS','clientes','direcciones_ver'),
('clientes.direcciones.administrar','Administrar direcciones de clientes','Crear y modificar direcciones de clientes','VENTAS','clientes','direcciones_administrar')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,
    modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE r.codigo='ADMINISTRADOR' AND p.recurso='clientes'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;

INSERT INTO empresa_modulos(tenant_id,empresa_id,module_key,enabled)
SELECT e.tenant_id,e.id,'VENTAS',TRUE FROM empresas e WHERE upper(e.codigo)='DEMO'
ON CONFLICT(empresa_id,module_key) DO UPDATE SET enabled=TRUE,updated_at=CURRENT_TIMESTAMP;
