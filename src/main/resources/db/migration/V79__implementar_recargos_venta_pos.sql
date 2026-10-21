ALTER TABLE product ADD COLUMN allow_surcharges BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE sales_surcharge_configuration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL REFERENCES empresas(id), enabled BOOLEAN NOT NULL DEFAULT TRUE,
    allow_sale_surcharge BOOLEAN NOT NULL DEFAULT TRUE, allow_line_surcharge BOOLEAN NOT NULL DEFAULT TRUE,
    allow_percentage BOOLEAN NOT NULL DEFAULT TRUE, allow_amount BOOLEAN NOT NULL DEFAULT TRUE,
    require_reason BOOLEAN NOT NULL DEFAULT TRUE, allow_authorization BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_by UUID, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_sales_surcharge_configuration_company UNIQUE (tenant_id, empresa_id)
);

CREATE TABLE sales_surcharge_role_limit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL REFERENCES empresas(id), role_id UUID NOT NULL REFERENCES roles(id),
    max_percentage NUMERIC(9,4), max_amount NUMERIC(19,4), active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_by UUID, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_sales_surcharge_role_limit UNIQUE (tenant_id, empresa_id, role_id),
    CONSTRAINT ck_sales_surcharge_role_percentage CHECK (max_percentage IS NULL OR (max_percentage >= 0 AND max_percentage <= 100)),
    CONSTRAINT ck_sales_surcharge_role_amount CHECK (max_amount IS NULL OR max_amount >= 0)
);

CREATE TABLE sales_surcharge_type (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL REFERENCES empresas(id), name VARCHAR(120) NOT NULL,
    normalized_name VARCHAR(120) NOT NULL, default_type VARCHAR(20) NOT NULL,
    default_value NUMERIC(19,6), tax_treatment VARCHAR(30) NOT NULL DEFAULT 'FOLLOW_LINE',
    editable_value BOOLEAN NOT NULL DEFAULT TRUE, active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_by UUID, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_sales_surcharge_type_name UNIQUE (tenant_id, empresa_id, normalized_name),
    CONSTRAINT ck_sales_surcharge_type_default CHECK (default_type IN ('PERCENTAGE','AMOUNT')),
    CONSTRAINT ck_sales_surcharge_type_tax CHECK (tax_treatment IN ('FOLLOW_LINE','TAXABLE','EXEMPT'))
);

CREATE TABLE sales_surcharge_reason (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL REFERENCES empresas(id), name VARCHAR(120) NOT NULL,
    normalized_name VARCHAR(120) NOT NULL, other_reason BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_sales_surcharge_reason_name UNIQUE (tenant_id, empresa_id, normalized_name)
);

INSERT INTO sales_surcharge_configuration(tenant_id, empresa_id)
SELECT tenant_id,id FROM empresas ON CONFLICT DO NOTHING;

INSERT INTO sales_surcharge_type(tenant_id,empresa_id,name,normalized_name,default_type,default_value,tax_treatment)
SELECT e.tenant_id,e.id,x.name,x.normalized_name,x.default_type,x.default_value,'FOLLOW_LINE'
FROM empresas e CROSS JOIN (VALUES
 ('Servicio especial','servicio especial','PERCENTAGE',5.000000),
 ('Entrega especial','entrega especial','AMOUNT',200.000000),
 ('Personalización','personalizacion','PERCENTAGE',10.000000)
) x(name,normalized_name,default_type,default_value) ON CONFLICT DO NOTHING;

INSERT INTO sales_surcharge_reason(tenant_id,empresa_id,name,normalized_name,other_reason)
SELECT e.tenant_id,e.id,x.name,x.normalized_name,x.other_reason
FROM empresas e CROSS JOIN (VALUES
 ('Servicio especial','servicio especial',FALSE),('Entrega especial','entrega especial',FALSE),
 ('Ajuste comercial','ajuste comercial',FALSE),('Otro','otro',TRUE)
) x(name,normalized_name,other_reason) ON CONFLICT DO NOTHING;

INSERT INTO sales_surcharge_role_limit(tenant_id,empresa_id,role_id,max_percentage,max_amount)
SELECT e.tenant_id,r.empresa_id,r.id,100.0000,NULL FROM roles r JOIN empresas e ON e.id=r.empresa_id
WHERE upper(r.codigo)='ADMINISTRADOR' ON CONFLICT DO NOTHING;

CREATE TABLE sales_surcharge_authorization (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL REFERENCES empresas(id), operation_key UUID NOT NULL,
    requested_by UUID NOT NULL REFERENCES usuarios(id), authorized_by UUID NOT NULL REFERENCES usuarios(id),
    requester_role_id UUID NOT NULL REFERENCES roles(id), authorizer_role_id UUID NOT NULL REFERENCES roles(id),
    scope VARCHAR(20) NOT NULL, surcharge_type VARCHAR(20) NOT NULL,
    concept_id UUID NOT NULL REFERENCES sales_surcharge_type(id), requested_value NUMERIC(19,6) NOT NULL,
    eligible_base NUMERIC(19,4) NOT NULL, surcharge_amount NUMERIC(19,4) NOT NULL,
    requester_max_percentage NUMERIC(9,4), requester_max_amount NUMERIC(19,4),
    authorizer_max_percentage NUMERIC(9,4), authorizer_max_amount NUMERIC(19,4),
    reason_id UUID REFERENCES sales_surcharge_reason(id), reason_name VARCHAR(120), reason_detail VARCHAR(500),
    fingerprint VARCHAR(64) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'AUTHORIZED',
    authorized_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, consumed_at TIMESTAMPTZ,
    sales_invoice_id UUID REFERENCES sales_invoice(id),
    CONSTRAINT ck_sales_surcharge_authorization_scope CHECK (scope IN ('SALE','LINE')),
    CONSTRAINT ck_sales_surcharge_authorization_type CHECK (surcharge_type IN ('PERCENTAGE','AMOUNT')),
    CONSTRAINT ck_sales_surcharge_authorization_status CHECK (status IN ('AUTHORIZED','CONSUMED','CANCELLED'))
);

ALTER TABLE sales_invoice_line
 ADD COLUMN surcharge_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
 ADD COLUMN surcharge_type VARCHAR(20), ADD COLUMN surcharge_value NUMERIC(19,6),
 ADD COLUMN surcharge_concept_id UUID REFERENCES sales_surcharge_type(id),
 ADD COLUMN surcharge_concept_name VARCHAR(120), ADD COLUMN surcharge_tax_treatment VARCHAR(30),
 ADD COLUMN surcharge_reason_id UUID REFERENCES sales_surcharge_reason(id),
 ADD COLUMN surcharge_reason_name VARCHAR(120), ADD COLUMN surcharge_reason_detail VARCHAR(500),
 ADD COLUMN surcharge_authorization_id UUID REFERENCES sales_surcharge_authorization(id),
 ADD COLUMN surcharge_authorized_by UUID REFERENCES usuarios(id), ADD COLUMN surcharge_authorized_at TIMESTAMPTZ;

ALTER TABLE saved_sale
 ADD COLUMN surcharge_scope VARCHAR(20), ADD COLUMN surcharge_type VARCHAR(20),
 ADD COLUMN surcharge_value NUMERIC(19,6), ADD COLUMN surcharge_concept_id UUID REFERENCES sales_surcharge_type(id),
 ADD COLUMN surcharge_reason_id UUID REFERENCES sales_surcharge_reason(id), ADD COLUMN surcharge_reason_name VARCHAR(120),
 ADD COLUMN surcharge_reason_detail VARCHAR(500), ADD COLUMN surcharge_authorization_id UUID REFERENCES sales_surcharge_authorization(id);

ALTER TABLE saved_sale_line ADD COLUMN allow_surcharges_snapshot BOOLEAN NOT NULL DEFAULT TRUE,
 ADD COLUMN surcharge_amount NUMERIC(19,4) NOT NULL DEFAULT 0;

CREATE INDEX idx_sales_surcharge_role_company ON sales_surcharge_role_limit(tenant_id,empresa_id);
CREATE INDEX idx_sales_surcharge_type_company ON sales_surcharge_type(tenant_id,empresa_id,active);
CREATE INDEX idx_sales_surcharge_reason_company ON sales_surcharge_reason(tenant_id,empresa_id,active);
CREATE INDEX idx_sales_surcharge_authorization_operation ON sales_surcharge_authorization(tenant_id,empresa_id,operation_key,status);

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('recargos_configuracion.ver','Ver configuración de recargos','Consultar políticas, tipos, límites y motivos de recargos','VENTAS','facturas','configurar_recargos'),
('recargos_configuracion.editar','Editar configuración de recargos','Modificar políticas, tipos, límites y motivos de recargos','VENTAS','facturas','editar_configuracion_recargos'),
('facturas.recargos.aplicar','Aplicar recargos','Aplicar recargos dentro de los límites del rol','VENTAS','facturas','aplicar_recargos'),
('facturas.recargos.solicitar_autorizacion','Solicitar autorización de recargo','Solicitar autorización cuando el recargo supera el límite propio','VENTAS','facturas','solicitar_autorizacion_recargo'),
('facturas.recargos.autorizar','Autorizar recargos','Autorizar recargos dentro de los límites del rol autorizador','VENTAS','facturas','autorizar_recargos')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE upper(r.codigo)='ADMINISTRADOR' AND p.codigo IN (
 'recargos_configuracion.ver','recargos_configuracion.editar','facturas.recargos.aplicar',
 'facturas.recargos.solicitar_autorizacion','facturas.recargos.autorizar') ON CONFLICT DO NOTHING;
