INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('cuentas_pagar.ver','Ver cuentas por pagar','Consultar las obligaciones pendientes con proveedores','COMPRAS','cuentas_pagar','ver'),
('cuentas_pagar.ver_detalle','Ver detalle de cuentas por pagar','Consultar el detalle de saldos por proveedor y factura','COMPRAS','cuentas_pagar','ver_detalle'),
('cuentas_pagar.ver_antiguedad','Ver antigüedad de cuentas por pagar','Consultar la antigüedad de saldos por vencimiento','COMPRAS','cuentas_pagar','ver_antiguedad'),
('cuentas_pagar.ver_movimientos','Ver movimientos de cuentas por pagar','Consultar los movimientos que originan y reducen cada saldo','COMPRAS','cuentas_pagar','ver_movimientos'),
('cuentas_pagar.exportar','Exportar cuentas por pagar','Exportar reportes de cuentas por pagar cuando estén disponibles','COMPRAS','cuentas_pagar','exportar')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,
    modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE upper(r.codigo)='ADMINISTRADOR' AND p.recurso='cuentas_pagar'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_accounts_payable_tenant_due
    ON accounts_payable(tenant_id,empresa_id,due_date,purchase_invoice_id)
    WHERE voided=false;

CREATE INDEX IF NOT EXISTS idx_purchase_invoice_cxp_cutoff
    ON purchase_invoice(tenant_id,empresa_id,registered_at,branch_id,currency_id,supplier_id)
    WHERE registered_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_supplier_payment_application_cxp_history
    ON supplier_payment_application(tenant_id,empresa_id,purchase_invoice_id,applied_at,reversed_at);

CREATE INDEX IF NOT EXISTS idx_supplier_credit_application_cxp_history
    ON supplier_credit_application(tenant_id,empresa_id,purchase_invoice_id,applied_at,reversed_at);
