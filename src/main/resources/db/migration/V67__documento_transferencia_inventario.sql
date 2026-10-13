ALTER TABLE inventory_transfer
    ADD COLUMN source_warehouse_name_snapshot VARCHAR(200),
    ADD COLUMN source_branch_name_snapshot VARCHAR(200),
    ADD COLUMN destination_warehouse_name_snapshot VARCHAR(200),
    ADD COLUMN destination_branch_name_snapshot VARCHAR(200),
    ADD COLUMN dispatched_by_name_snapshot VARCHAR(200),
    ADD COLUMN received_by_name_snapshot VARCHAR(200);

ALTER TABLE inventory_transfer_line
    ADD COLUMN product_name_snapshot VARCHAR(300);

UPDATE inventory_transfer_line
SET product_name_snapshot = description_snapshot
WHERE product_name_snapshot IS NULL;

ALTER TABLE inventory_transfer_line
    ALTER COLUMN product_name_snapshot SET NOT NULL;

UPDATE inventory_transfer t
SET source_warehouse_name_snapshot = source_w.name,
    source_branch_name_snapshot = source_b.nombre,
    destination_warehouse_name_snapshot = destination_w.name,
    destination_branch_name_snapshot = destination_b.nombre
FROM warehouse source_w
JOIN sucursales source_b ON source_b.id = source_w.branch_id,
     warehouse destination_w
JOIN sucursales destination_b ON destination_b.id = destination_w.branch_id
WHERE t.source_warehouse_id = source_w.id
  AND t.destination_warehouse_id = destination_w.id
  AND t.status <> 'DRAFT';

UPDATE inventory_transfer t
SET dispatched_by_name_snapshot = trim(concat_ws(' ', u.nombre, u.apellido))
FROM usuarios u
WHERE t.dispatched_by = u.id
  AND t.dispatched_by_name_snapshot IS NULL;

UPDATE inventory_transfer t
SET received_by_name_snapshot = trim(concat_ws(' ', u.nombre, u.apellido))
FROM usuarios u
WHERE t.received_by = u.id
  AND t.received_by_name_snapshot IS NULL;

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion)
VALUES ('inventario_transferencias.pdf','Generar PDF de transferencias',
        'Generar y reimprimir el documento oficial de una transferencia de inventario',
        'INVENTARIO','inventario_transferencias','pdf')
ON CONFLICT(codigo) DO UPDATE SET
    nombre=EXCLUDED.nombre,
    descripcion=EXCLUDED.descripcion,
    modulo=EXCLUDED.modulo,
    recurso=EXCLUDED.recurso,
    accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id
FROM roles r
JOIN empresas e ON e.id=r.empresa_id
JOIN permisos p ON p.codigo='inventario_transferencias.pdf'
WHERE upper(r.codigo)='ADMINISTRADOR'
  AND (upper(e.codigo)='DEMO' OR upper(e.nombre)='DEMO')
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
