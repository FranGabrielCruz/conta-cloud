CREATE TABLE product_category (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_product_category_name CHECK (btrim(name) <> ''),
    CONSTRAINT fk_product_category_empresa FOREIGN KEY (tenant_id,empresa_id)
        REFERENCES empresas(tenant_id,id),
    CONSTRAINT fk_product_category_created_by FOREIGN KEY (created_by) REFERENCES usuarios(id),
    CONSTRAINT fk_product_category_updated_by FOREIGN KEY (updated_by) REFERENCES usuarios(id),
    CONSTRAINT uk_product_category_scope_id UNIQUE (tenant_id,empresa_id,id)
);
CREATE UNIQUE INDEX uk_product_category_name ON product_category(tenant_id,empresa_id,lower(btrim(name)));
CREATE INDEX idx_product_category_list ON product_category(tenant_id,empresa_id,active,name);

CREATE TABLE unit_of_measure (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    name VARCHAR(80) NOT NULL,
    abbreviation VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_unit_of_measure_name CHECK (btrim(name) <> ''),
    CONSTRAINT ck_unit_of_measure_abbreviation CHECK (btrim(abbreviation) <> ''),
    CONSTRAINT fk_unit_of_measure_empresa FOREIGN KEY (tenant_id,empresa_id)
        REFERENCES empresas(tenant_id,id),
    CONSTRAINT fk_unit_of_measure_created_by FOREIGN KEY (created_by) REFERENCES usuarios(id),
    CONSTRAINT fk_unit_of_measure_updated_by FOREIGN KEY (updated_by) REFERENCES usuarios(id),
    CONSTRAINT uk_unit_of_measure_abbreviation UNIQUE (tenant_id,empresa_id,abbreviation),
    CONSTRAINT uk_unit_of_measure_scope_id UNIQUE (tenant_id,empresa_id,id)
);
CREATE INDEX idx_unit_of_measure_list ON unit_of_measure(tenant_id,empresa_id,active,name);

CREATE TABLE product_sequence (
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    product_last_number BIGINT NOT NULL DEFAULT 0 CHECK (product_last_number >= 0),
    service_last_number BIGINT NOT NULL DEFAULT 0 CHECK (service_last_number >= 0),
    PRIMARY KEY (tenant_id,empresa_id),
    CONSTRAINT fk_product_sequence_empresa FOREIGN KEY (tenant_id,empresa_id)
        REFERENCES empresas(tenant_id,id)
);

CREATE TABLE product (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    code VARCHAR(24) NOT NULL,
    name VARCHAR(180) NOT NULL,
    product_type VARCHAR(20) NOT NULL,
    category_id UUID,
    unit_of_measure_id UUID NOT NULL,
    barcode VARCHAR(100),
    description VARCHAR(1000),
    purchase_cost NUMERIC(19,4),
    sale_price NUMERIC(19,4),
    currency_id UUID,
    purchase_tax_id UUID,
    sales_tax_id UUID,
    track_inventory BOOLEAN NOT NULL DEFAULT TRUE,
    allow_negative_stock BOOLEAN NOT NULL DEFAULT FALSE,
    minimum_stock NUMERIC(19,4),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_product_name CHECK (btrim(name) <> ''),
    CONSTRAINT ck_product_type CHECK (product_type IN ('PRODUCT','SERVICE')),
    CONSTRAINT ck_product_amounts CHECK (
        (purchase_cost IS NULL OR purchase_cost >= 0) AND
        (sale_price IS NULL OR sale_price >= 0) AND
        (minimum_stock IS NULL OR minimum_stock >= 0)
    ),
    CONSTRAINT ck_product_currency CHECK (currency_id IS NOT NULL OR (purchase_cost IS NULL AND sale_price IS NULL)),
    CONSTRAINT ck_product_inventory CHECK (
        (product_type = 'PRODUCT' OR (track_inventory = FALSE AND allow_negative_stock = FALSE AND minimum_stock IS NULL)) AND
        (track_inventory = TRUE OR allow_negative_stock = FALSE)
    ),
    CONSTRAINT fk_product_empresa FOREIGN KEY (tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
    CONSTRAINT fk_product_category FOREIGN KEY (tenant_id,empresa_id,category_id)
        REFERENCES product_category(tenant_id,empresa_id,id),
    CONSTRAINT fk_product_unit FOREIGN KEY (tenant_id,empresa_id,unit_of_measure_id)
        REFERENCES unit_of_measure(tenant_id,empresa_id,id),
    CONSTRAINT fk_product_currency FOREIGN KEY (empresa_id,currency_id) REFERENCES monedas(empresa_id,id),
    CONSTRAINT fk_product_purchase_tax FOREIGN KEY (empresa_id,purchase_tax_id) REFERENCES impuestos(empresa_id,id),
    CONSTRAINT fk_product_sales_tax FOREIGN KEY (empresa_id,sales_tax_id) REFERENCES impuestos(empresa_id,id),
    CONSTRAINT fk_product_created_by FOREIGN KEY (created_by) REFERENCES usuarios(id),
    CONSTRAINT fk_product_updated_by FOREIGN KEY (updated_by) REFERENCES usuarios(id),
    CONSTRAINT uk_product_code UNIQUE (tenant_id,empresa_id,code),
    CONSTRAINT uk_product_scope_id UNIQUE (tenant_id,empresa_id,id)
);
CREATE UNIQUE INDEX uk_product_barcode ON product(tenant_id,empresa_id,barcode) WHERE barcode IS NOT NULL;
CREATE INDEX idx_product_list ON product(tenant_id,empresa_id,active,name);
CREATE INDEX idx_product_category ON product(tenant_id,empresa_id,category_id);
CREATE INDEX idx_product_type ON product(tenant_id,empresa_id,product_type,active);

CREATE TRIGGER trg_product_category_tenant_empresa
BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON product_category
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();
CREATE TRIGGER trg_unit_of_measure_tenant_empresa
BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON unit_of_measure
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();
CREATE TRIGGER trg_product_tenant_empresa
BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON product
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();

INSERT INTO unit_of_measure(tenant_id,empresa_id,name,abbreviation)
SELECT e.tenant_id,e.id,u.name,u.abbreviation
FROM empresas e CROSS JOIN (VALUES
    ('Unidad','und'),('Caja','caja'),('Paquete','paq'),('Kilogramo','kg'),('Libra','lb'),
    ('Metro','m'),('Litro','L'),('Galón','gal'),('Hora','h'),('Servicio','srv')
) AS u(name,abbreviation)
ON CONFLICT(tenant_id,empresa_id,abbreviation) DO NOTHING;

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('productos.ver','Ver productos','Consultar productos y servicios','INVENTARIO','productos','ver'),
('productos.crear','Crear productos','Crear productos y servicios','INVENTARIO','productos','crear'),
('productos.editar','Editar productos','Modificar productos y servicios','INVENTARIO','productos','editar'),
('productos.desactivar','Desactivar productos','Desactivar productos y servicios','INVENTARIO','productos','desactivar'),
('productos.reactivar','Reactivar productos','Reactivar productos y servicios','INVENTARIO','productos','reactivar')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,
    modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r JOIN permisos p ON p.recurso='productos'
WHERE r.codigo='ADMINISTRADOR'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;

INSERT INTO empresa_modulos(tenant_id,empresa_id,module_key,enabled)
SELECT e.tenant_id,e.id,'INVENTARIO',TRUE FROM empresas e WHERE upper(e.codigo)='DEMO'
ON CONFLICT(empresa_id,module_key) DO UPDATE SET enabled=TRUE,updated_at=CURRENT_TIMESTAMP;
