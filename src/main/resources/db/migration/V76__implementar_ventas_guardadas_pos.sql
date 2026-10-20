CREATE TABLE saved_sale_sequence (
 tenant_id UUID NOT NULL, empresa_id UUID NOT NULL, last_number BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(tenant_id,empresa_id),
 CONSTRAINT fk_saved_sale_sequence_empresa FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id)
);

CREATE TABLE saved_sale (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL, empresa_id UUID NOT NULL,
 numero VARCHAR(30) NOT NULL, estado VARCHAR(20) NOT NULL DEFAULT 'SAVED',
 branch_id UUID NOT NULL, cash_register_id UUID NOT NULL, terminal_id UUID NOT NULL, warehouse_id UUID NOT NULL,
 customer_id UUID, customer_name VARCHAR(180) NOT NULL, customer_identification VARCHAR(50),
 currency_id UUID NOT NULL, currency_code VARCHAR(3) NOT NULL,
 referencia VARCHAR(150), observacion VARCHAR(1000),
 subtotal NUMERIC(19,4) NOT NULL, descuento NUMERIC(19,4) NOT NULL DEFAULT 0,
 recargo NUMERIC(19,4) NOT NULL DEFAULT 0, impuestos NUMERIC(19,4) NOT NULL DEFAULT 0,
 total NUMERIC(19,4) NOT NULL, item_count INTEGER NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, created_by UUID NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL, updated_by UUID NOT NULL,
 recovered_at TIMESTAMPTZ, recovered_by UUID, recovered_terminal_id UUID,
 completed_at TIMESTAMPTZ, completed_invoice_id UUID,
 discarded_at TIMESTAMPTZ, discarded_by UUID, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT ck_saved_sale_status CHECK(estado IN('SAVED','IN_USE','COMPLETED','DISCARDED')),
 CONSTRAINT ck_saved_sale_amounts CHECK(subtotal>=0 AND descuento>=0 AND recargo>=0 AND impuestos>=0 AND total>=0 AND item_count>0),
 CONSTRAINT uk_saved_sale_number UNIQUE(tenant_id,empresa_id,numero),
 CONSTRAINT uk_saved_sale_scope UNIQUE(tenant_id,empresa_id,id),
 CONSTRAINT fk_saved_sale_empresa FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
 CONSTRAINT fk_saved_sale_branch FOREIGN KEY(empresa_id,branch_id) REFERENCES sucursales(empresa_id,id),
 CONSTRAINT fk_saved_sale_cash FOREIGN KEY(tenant_id,empresa_id,cash_register_id) REFERENCES cajas(tenant_id,empresa_id,id),
 CONSTRAINT fk_saved_sale_terminal FOREIGN KEY(tenant_id,empresa_id,terminal_id) REFERENCES pos_terminal(tenant_id,empresa_id,id),
 CONSTRAINT fk_saved_sale_warehouse FOREIGN KEY(tenant_id,empresa_id,warehouse_id) REFERENCES warehouse(tenant_id,empresa_id,id),
 CONSTRAINT fk_saved_sale_customer FOREIGN KEY(tenant_id,empresa_id,customer_id) REFERENCES customer(tenant_id,empresa_id,id),
 CONSTRAINT fk_saved_sale_currency FOREIGN KEY(empresa_id,currency_id) REFERENCES monedas(empresa_id,id),
 CONSTRAINT fk_saved_sale_created_by FOREIGN KEY(created_by) REFERENCES usuarios(id),
 CONSTRAINT fk_saved_sale_updated_by FOREIGN KEY(updated_by) REFERENCES usuarios(id),
 CONSTRAINT fk_saved_sale_recovered_by FOREIGN KEY(recovered_by) REFERENCES usuarios(id),
 CONSTRAINT fk_saved_sale_recovered_terminal FOREIGN KEY(tenant_id,empresa_id,recovered_terminal_id) REFERENCES pos_terminal(tenant_id,empresa_id,id),
 CONSTRAINT fk_saved_sale_completed_invoice FOREIGN KEY(tenant_id,empresa_id,completed_invoice_id) REFERENCES sales_invoice(tenant_id,empresa_id,id),
 CONSTRAINT fk_saved_sale_discarded_by FOREIGN KEY(discarded_by) REFERENCES usuarios(id)
);
CREATE INDEX idx_saved_sale_pending ON saved_sale(tenant_id,empresa_id,branch_id,estado,updated_at DESC,id DESC);
CREATE INDEX idx_saved_sale_creator ON saved_sale(tenant_id,empresa_id,created_by,estado);

CREATE TABLE saved_sale_line (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL, empresa_id UUID NOT NULL,
 saved_sale_id UUID NOT NULL, product_id UUID NOT NULL, product_code VARCHAR(24) NOT NULL,
 product_name VARCHAR(300) NOT NULL, unit_name VARCHAR(80) NOT NULL,
 cantidad NUMERIC(19,4) NOT NULL, precio NUMERIC(19,4) NOT NULL,
 tax_rate NUMERIC(9,6) NOT NULL DEFAULT 0, subtotal NUMERIC(19,4) NOT NULL,
 impuesto NUMERIC(19,4) NOT NULL DEFAULT 0, total NUMERIC(19,4) NOT NULL, line_order INTEGER NOT NULL,
 CONSTRAINT ck_saved_sale_line_values CHECK(cantidad>0 AND precio>=0 AND tax_rate>=0 AND subtotal>=0 AND impuesto>=0 AND total>=0 AND line_order>0),
 CONSTRAINT fk_saved_sale_line_sale FOREIGN KEY(tenant_id,empresa_id,saved_sale_id) REFERENCES saved_sale(tenant_id,empresa_id,id) ON DELETE CASCADE,
 CONSTRAINT fk_saved_sale_line_product FOREIGN KEY(tenant_id,empresa_id,product_id) REFERENCES product(tenant_id,empresa_id,id),
 CONSTRAINT uk_saved_sale_line_order UNIQUE(saved_sale_id,line_order)
);
CREATE INDEX idx_saved_sale_line_sale ON saved_sale_line(tenant_id,empresa_id,saved_sale_id,line_order);

CREATE TRIGGER trg_saved_sale_tenant_empresa BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON saved_sale
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();
CREATE TRIGGER trg_saved_sale_line_tenant_empresa BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON saved_sale_line
FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa();

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('ventas_guardadas.guardar','Guardar ventas','Guardar temporalmente una venta del punto de venta','VENTAS','ventas_guardadas','guardar'),
('ventas_guardadas.consultar','Consultar ventas guardadas','Consultar las ventas guardadas disponibles','VENTAS','ventas_guardadas','consultar'),
('ventas_guardadas.recuperar','Recuperar ventas guardadas','Recuperar una venta para continuarla','VENTAS','ventas_guardadas','recuperar'),
('ventas_guardadas.recuperar_otros','Recuperar ventas de otros usuarios','Consultar y recuperar ventas guardadas por otros usuarios','VENTAS','ventas_guardadas','recuperar_otros'),
('ventas_guardadas.descartar','Descartar ventas guardadas','Descartar una venta guardada pendiente','VENTAS','ventas_guardadas','descartar')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE upper(r.codigo)='ADMINISTRADOR' AND p.recurso='ventas_guardadas'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
