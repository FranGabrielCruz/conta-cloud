CREATE UNIQUE INDEX IF NOT EXISTS uk_condiciones_pago_empresa_id
    ON condiciones_pago(empresa_id,id);

CREATE TABLE supplier (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    code VARCHAR(24) NOT NULL,
    trade_name VARCHAR(150) NOT NULL,
    legal_name VARCHAR(180),
    tax_identification VARCHAR(50),
    tax_identification_normalized VARCHAR(50),
    supplier_type VARCHAR(20) NOT NULL,
    phone VARCHAR(30),
    email VARCHAR(180),
    payment_term_id UUID,
    currency_id UUID,
    address VARCHAR(500),
    contact_name VARCHAR(150),
    contact_phone VARCHAR(30),
    notes VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_supplier_type CHECK (supplier_type IN ('NATIONAL','FOREIGN')),
    CONSTRAINT ck_supplier_trade_name CHECK (btrim(trade_name)<>''),
    CONSTRAINT fk_supplier_tenant_empresa FOREIGN KEY (tenant_id,empresa_id)
        REFERENCES empresas(tenant_id,id),
    CONSTRAINT fk_supplier_payment_term FOREIGN KEY (empresa_id,payment_term_id)
        REFERENCES condiciones_pago(empresa_id,id),
    CONSTRAINT fk_supplier_currency FOREIGN KEY (empresa_id,currency_id)
        REFERENCES monedas(empresa_id,id),
    CONSTRAINT fk_supplier_created_by FOREIGN KEY (created_by) REFERENCES usuarios(id),
    CONSTRAINT fk_supplier_updated_by FOREIGN KEY (updated_by) REFERENCES usuarios(id),
    CONSTRAINT uk_supplier_code UNIQUE (tenant_id,empresa_id,code)
);

CREATE UNIQUE INDEX uk_supplier_tenant_empresa_id ON supplier(tenant_id,empresa_id,id);
CREATE UNIQUE INDEX uk_supplier_tax_identification
    ON supplier(tenant_id,empresa_id,tax_identification_normalized)
    WHERE tax_identification_normalized IS NOT NULL;
CREATE INDEX idx_supplier_list ON supplier(tenant_id,empresa_id,active,trade_name);
CREATE INDEX idx_supplier_legal_name ON supplier(tenant_id,empresa_id,legal_name);
CREATE INDEX idx_supplier_email ON supplier(tenant_id,empresa_id,email);

CREATE TRIGGER trg_supplier_tenant_empresa
BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON supplier
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('proveedores.ver','Ver proveedores','Consultar proveedores','COMPRAS','proveedores','ver'),
('proveedores.crear','Crear proveedores','Crear proveedores','COMPRAS','proveedores','crear'),
('proveedores.editar','Editar proveedores','Modificar proveedores','COMPRAS','proveedores','editar'),
('proveedores.desactivar','Desactivar proveedores','Desactivar proveedores','COMPRAS','proveedores','desactivar'),
('proveedores.reactivar','Reactivar proveedores','Reactivar proveedores','COMPRAS','proveedores','reactivar')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,
    modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r JOIN permisos p ON p.recurso='proveedores'
WHERE r.codigo='ADMINISTRADOR'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;

INSERT INTO empresa_modulos(tenant_id,empresa_id,module_key,enabled)
SELECT e.tenant_id,e.id,'COMPRAS',TRUE FROM empresas e WHERE upper(e.codigo)='DEMO'
ON CONFLICT(empresa_id,module_key) DO UPDATE SET enabled=TRUE,updated_at=CURRENT_TIMESTAMP;
