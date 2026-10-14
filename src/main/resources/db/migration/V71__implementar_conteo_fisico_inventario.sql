CREATE TABLE inventory_physical_count_sequence(
 tenant_id UUID NOT NULL, empresa_id UUID NOT NULL, last_number BIGINT NOT NULL DEFAULT 0 CHECK(last_number>=0),
 PRIMARY KEY(tenant_id,empresa_id), CONSTRAINT fk_count_sequence_empresa FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id)
);

CREATE TABLE inventory_physical_count(
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL, empresa_id UUID NOT NULL,
 count_number VARCHAR(30) NOT NULL, count_date DATE NOT NULL, warehouse_id UUID NOT NULL, category_id UUID,
 count_type VARCHAR(12) NOT NULL, count_mode VARCHAR(10) NOT NULL, reference VARCHAR(100), notes VARCHAR(1000),
 status VARCHAR(24) NOT NULL DEFAULT 'DRAFT', warehouse_name_snapshot VARCHAR(200), branch_name_snapshot VARCHAR(200),
 started_at TIMESTAMPTZ, started_by UUID, finished_at TIMESTAMPTZ, finished_by UUID,
 confirmed_at TIMESTAMPTZ, confirmed_by UUID, confirmed_by_name_snapshot VARCHAR(200),
 cancelled_at TIMESTAMPTZ, cancelled_by UUID, cancellation_reason VARCHAR(500),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, created_by UUID NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_by UUID NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT ck_count_type CHECK(count_type IN('COMPLETE','PARTIAL')),
 CONSTRAINT ck_count_mode CHECK(count_mode IN('VISIBLE','BLIND')),
 CONSTRAINT ck_count_status CHECK(status IN('DRAFT','IN_PROGRESS','PENDING_CONFIRMATION','CONFIRMED','CANCELLED')),
 CONSTRAINT fk_count_empresa FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id),
 CONSTRAINT fk_count_warehouse FOREIGN KEY(tenant_id,empresa_id,warehouse_id) REFERENCES warehouse(tenant_id,empresa_id,id),
 CONSTRAINT fk_count_category FOREIGN KEY(tenant_id,empresa_id,category_id) REFERENCES product_category(tenant_id,empresa_id,id),
 CONSTRAINT fk_count_started_by FOREIGN KEY(started_by) REFERENCES usuarios(id),
 CONSTRAINT fk_count_finished_by FOREIGN KEY(finished_by) REFERENCES usuarios(id),
 CONSTRAINT fk_count_confirmed_by FOREIGN KEY(confirmed_by) REFERENCES usuarios(id),
 CONSTRAINT fk_count_cancelled_by FOREIGN KEY(cancelled_by) REFERENCES usuarios(id),
 CONSTRAINT fk_count_created_by FOREIGN KEY(created_by) REFERENCES usuarios(id),
 CONSTRAINT fk_count_updated_by FOREIGN KEY(updated_by) REFERENCES usuarios(id),
 CONSTRAINT uk_count_number UNIQUE(tenant_id,empresa_id,count_number),
 CONSTRAINT uk_count_scope UNIQUE(tenant_id,empresa_id,id)
);

CREATE TABLE inventory_physical_count_line(
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), inventory_physical_count_id UUID NOT NULL,
 tenant_id UUID NOT NULL, empresa_id UUID NOT NULL, product_id UUID NOT NULL, unit_of_measure_id UUID NOT NULL,
 initial_system_quantity NUMERIC(19,4) NOT NULL, counted_quantity NUMERIC(19,4),
 final_counted_quantity NUMERIC(19,4), theoretical_quantity NUMERIC(19,4), difference_quantity NUMERIC(19,4),
 product_code_snapshot VARCHAR(24) NOT NULL, product_name_snapshot VARCHAR(300) NOT NULL,
 unit_snapshot VARCHAR(120) NOT NULL, counted_at TIMESTAMPTZ, counted_by UUID, line_order INTEGER NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT ck_count_line_nonnegative CHECK(counted_quantity IS NULL OR counted_quantity>=0),
 CONSTRAINT fk_count_line_header FOREIGN KEY(tenant_id,empresa_id,inventory_physical_count_id) REFERENCES inventory_physical_count(tenant_id,empresa_id,id) ON DELETE CASCADE,
 CONSTRAINT fk_count_line_product FOREIGN KEY(tenant_id,empresa_id,product_id) REFERENCES product(tenant_id,empresa_id,id),
 CONSTRAINT fk_count_line_unit FOREIGN KEY(tenant_id,empresa_id,unit_of_measure_id) REFERENCES unit_of_measure(tenant_id,empresa_id,id),
 CONSTRAINT fk_count_line_counted_by FOREIGN KEY(counted_by) REFERENCES usuarios(id),
 CONSTRAINT uk_count_line_order UNIQUE(inventory_physical_count_id,line_order),
 CONSTRAINT uk_count_line_product UNIQUE(inventory_physical_count_id,product_id)
);

CREATE TABLE inventory_physical_count_scope_product(
 inventory_physical_count_id UUID NOT NULL, tenant_id UUID NOT NULL, empresa_id UUID NOT NULL, product_id UUID NOT NULL,
 PRIMARY KEY(inventory_physical_count_id,product_id),
 CONSTRAINT fk_count_scope_header FOREIGN KEY(tenant_id,empresa_id,inventory_physical_count_id) REFERENCES inventory_physical_count(tenant_id,empresa_id,id) ON DELETE CASCADE,
 CONSTRAINT fk_count_scope_product FOREIGN KEY(tenant_id,empresa_id,product_id) REFERENCES product(tenant_id,empresa_id,id)
);

CREATE TABLE inventory_physical_count_attempt(
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), inventory_physical_count_line_id UUID NOT NULL,
 attempt_number INTEGER NOT NULL, counted_quantity NUMERIC(19,4) NOT NULL CHECK(counted_quantity>=0),
 counted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, counted_by UUID NOT NULL,
 CONSTRAINT fk_count_attempt_line FOREIGN KEY(inventory_physical_count_line_id) REFERENCES inventory_physical_count_line(id) ON DELETE CASCADE,
 CONSTRAINT fk_count_attempt_user FOREIGN KEY(counted_by) REFERENCES usuarios(id),
 CONSTRAINT uk_count_attempt UNIQUE(inventory_physical_count_line_id,attempt_number)
);

CREATE INDEX idx_count_list ON inventory_physical_count(tenant_id,empresa_id,count_date DESC,created_at DESC);
CREATE INDEX idx_count_filters ON inventory_physical_count(tenant_id,empresa_id,status,warehouse_id,count_type,count_date DESC);
CREATE INDEX idx_count_line_product ON inventory_physical_count_line(tenant_id,empresa_id,product_id);

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('conteo_fisico.ver','Ver conteos físicos','Consultar conteos físicos','INVENTARIO','conteo_fisico','ver'),
('conteo_fisico.crear','Crear conteos físicos','Crear conteos físicos','INVENTARIO','conteo_fisico','crear'),
('conteo_fisico.editar','Editar conteos físicos','Editar borradores de conteo','INVENTARIO','conteo_fisico','editar'),
('conteo_fisico.iniciar','Iniciar conteos físicos','Congelar alcance y saldos iniciales','INVENTARIO','conteo_fisico','iniciar'),
('conteo_fisico.contar','Capturar conteos físicos','Registrar cantidades físicas','INVENTARIO','conteo_fisico','contar'),
('conteo_fisico.finalizar','Finalizar conteos físicos','Enviar conteos a confirmación','INVENTARIO','conteo_fisico','finalizar'),
('conteo_fisico.recuento','Recontar productos','Registrar intentos de recuento','INVENTARIO','conteo_fisico','recuento'),
('conteo_fisico.confirmar','Confirmar conteos físicos','Confirmar y generar ajustes de inventario','INVENTARIO','conteo_fisico','confirmar'),
('conteo_fisico.anular','Anular conteos físicos','Anular conteos no confirmados','INVENTARIO','conteo_fisico','anular'),
('conteo_fisico.hoja_pdf','Generar hoja de conteo','Generar hoja de captura','INVENTARIO','conteo_fisico','hoja_pdf'),
('conteo_fisico.resultado_pdf','Generar resultado del conteo','Generar documento confirmado','INVENTARIO','conteo_fisico','resultado_pdf')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p JOIN empresas e ON e.id=r.empresa_id
WHERE upper(r.codigo)='ADMINISTRADOR' AND p.recurso='conteo_fisico' AND (upper(e.codigo)='DEMO' OR upper(e.nombre)='DEMO')
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
