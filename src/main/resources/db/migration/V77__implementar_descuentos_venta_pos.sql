ALTER TABLE product
    ADD COLUMN allow_discounts BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE sales_discount_configuration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL REFERENCES empresas(id),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    allow_sale_discount BOOLEAN NOT NULL DEFAULT TRUE,
    allow_line_discount BOOLEAN NOT NULL DEFAULT TRUE,
    allow_percentage BOOLEAN NOT NULL DEFAULT TRUE,
    allow_amount BOOLEAN NOT NULL DEFAULT TRUE,
    require_reason BOOLEAN NOT NULL DEFAULT TRUE,
    allow_authorization BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_sales_discount_configuration_company UNIQUE (tenant_id, empresa_id)
);

CREATE TABLE sales_discount_role_limit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL REFERENCES empresas(id),
    role_id UUID NOT NULL REFERENCES roles(id),
    max_percentage NUMERIC(9,4),
    max_amount NUMERIC(19,4),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_sales_discount_role_limit UNIQUE (tenant_id, empresa_id, role_id),
    CONSTRAINT ck_sales_discount_role_percentage CHECK (max_percentage IS NULL OR (max_percentage >= 0 AND max_percentage <= 100)),
    CONSTRAINT ck_sales_discount_role_amount CHECK (max_amount IS NULL OR max_amount >= 0)
);

CREATE TABLE sales_discount_reason (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL REFERENCES empresas(id),
    name VARCHAR(120) NOT NULL,
    normalized_name VARCHAR(120) NOT NULL,
    other_reason BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_sales_discount_reason_name UNIQUE (tenant_id, empresa_id, normalized_name)
);

INSERT INTO sales_discount_configuration(tenant_id, empresa_id)
SELECT e.tenant_id, e.id
FROM empresas e
ON CONFLICT (tenant_id, empresa_id) DO NOTHING;

INSERT INTO sales_discount_reason(tenant_id, empresa_id, name, normalized_name, other_reason)
SELECT e.tenant_id, e.id, x.name, lower(x.name), x.other_reason
FROM empresas e
CROSS JOIN (VALUES
    ('Promoción', FALSE),
    ('Cliente frecuente', FALSE),
    ('Autorización gerencial', FALSE),
    ('Producto con defecto', FALSE),
    ('Ajuste comercial', FALSE),
    ('Otro', TRUE)
) AS x(name, other_reason)
ON CONFLICT (tenant_id, empresa_id, normalized_name) DO NOTHING;

INSERT INTO sales_discount_role_limit(tenant_id, empresa_id, role_id, max_percentage, max_amount)
SELECT e.tenant_id, r.empresa_id, r.id, 100.0000, NULL
FROM roles r
JOIN empresas e ON e.id = r.empresa_id
WHERE upper(r.codigo) = 'ADMINISTRADOR'
ON CONFLICT (tenant_id, empresa_id, role_id) DO NOTHING;

CREATE TABLE sales_discount_authorization (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL REFERENCES empresas(id),
    operation_key UUID NOT NULL,
    requested_by UUID NOT NULL REFERENCES usuarios(id),
    authorized_by UUID NOT NULL REFERENCES usuarios(id),
    requester_role_id UUID NOT NULL REFERENCES roles(id),
    authorizer_role_id UUID NOT NULL REFERENCES roles(id),
    scope VARCHAR(20) NOT NULL,
    discount_type VARCHAR(20) NOT NULL,
    product_id UUID REFERENCES product(id),
    requested_value NUMERIC(19,6) NOT NULL,
    eligible_base NUMERIC(19,4) NOT NULL,
    discount_amount NUMERIC(19,4) NOT NULL,
    requester_max_percentage NUMERIC(9,4),
    requester_max_amount NUMERIC(19,4),
    authorizer_max_percentage NUMERIC(9,4),
    authorizer_max_amount NUMERIC(19,4),
    reason_id UUID REFERENCES sales_discount_reason(id),
    reason_name VARCHAR(120),
    reason_detail VARCHAR(500),
    fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AUTHORIZED',
    authorized_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    consumed_at TIMESTAMPTZ,
    sales_invoice_id UUID REFERENCES sales_invoice(id),
    CONSTRAINT ck_sales_discount_authorization_scope CHECK (scope IN ('SALE','LINE')),
    CONSTRAINT ck_sales_discount_authorization_type CHECK (discount_type IN ('PERCENTAGE','AMOUNT')),
    CONSTRAINT ck_sales_discount_authorization_status CHECK (status IN ('AUTHORIZED','CONSUMED','CANCELLED'))
);

CREATE INDEX idx_sales_discount_role_company ON sales_discount_role_limit(tenant_id, empresa_id);
CREATE INDEX idx_sales_discount_reason_company ON sales_discount_reason(tenant_id, empresa_id, active);
CREATE INDEX idx_sales_discount_authorization_operation ON sales_discount_authorization(tenant_id, empresa_id, operation_key, status);

ALTER TABLE sales_invoice_line
    ADD COLUMN gross_subtotal NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN taxable_base_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN discount_type VARCHAR(20),
    ADD COLUMN discount_value NUMERIC(19,6),
    ADD COLUMN discount_reason_id UUID REFERENCES sales_discount_reason(id),
    ADD COLUMN discount_reason_name VARCHAR(120),
    ADD COLUMN discount_reason_detail VARCHAR(500),
    ADD COLUMN discount_authorization_id UUID REFERENCES sales_discount_authorization(id),
    ADD COLUMN discount_authorized_by UUID REFERENCES usuarios(id),
    ADD COLUMN discount_authorized_at TIMESTAMPTZ;

UPDATE sales_invoice_line
SET gross_subtotal = subtotal,
    taxable_base_amount = subtotal;

ALTER TABLE saved_sale
    ADD COLUMN discount_scope VARCHAR(20),
    ADD COLUMN discount_type VARCHAR(20),
    ADD COLUMN discount_value NUMERIC(19,6),
    ADD COLUMN discount_product_id UUID REFERENCES product(id),
    ADD COLUMN discount_reason_id UUID REFERENCES sales_discount_reason(id),
    ADD COLUMN discount_reason_name VARCHAR(120),
    ADD COLUMN discount_reason_detail VARCHAR(500),
    ADD COLUMN discount_authorization_id UUID REFERENCES sales_discount_authorization(id);

ALTER TABLE saved_sale_line
    ADD COLUMN allow_discounts_snapshot BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN discount_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN taxable_base_amount NUMERIC(19,4) NOT NULL DEFAULT 0;

UPDATE saved_sale_line
SET taxable_base_amount = subtotal;

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('descuentos_configuracion.ver','Ver configuración de descuentos','Consultar políticas, límites y motivos de descuentos','VENTAS','facturas','configurar_descuentos'),
('descuentos_configuracion.editar','Editar configuración de descuentos','Modificar políticas, límites y motivos de descuentos','VENTAS','facturas','editar_configuracion_descuentos'),
('facturas.descuentos.aplicar','Aplicar descuentos','Aplicar descuentos dentro de los límites del rol','VENTAS','facturas','aplicar_descuentos'),
('facturas.descuentos.solicitar_autorizacion','Solicitar autorización de descuento','Solicitar autorización cuando el descuento supera el límite propio','VENTAS','facturas','solicitar_autorizacion_descuento'),
('facturas.descuentos.autorizar','Autorizar descuentos','Autorizar descuentos dentro de los límites del rol autorizador','VENTAS','facturas','autorizar_descuentos')
ON CONFLICT(codigo) DO UPDATE SET
    nombre=EXCLUDED.nombre,
    descripcion=EXCLUDED.descripcion,
    modulo=EXCLUDED.modulo,
    recurso=EXCLUDED.recurso,
    accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id
FROM roles r
CROSS JOIN permisos p
WHERE upper(r.codigo)='ADMINISTRADOR'
  AND p.codigo IN (
    'descuentos_configuracion.ver',
    'descuentos_configuracion.editar',
    'facturas.descuentos.aplicar',
    'facturas.descuentos.solicitar_autorizacion',
    'facturas.descuentos.autorizar'
  )
ON CONFLICT DO NOTHING;
