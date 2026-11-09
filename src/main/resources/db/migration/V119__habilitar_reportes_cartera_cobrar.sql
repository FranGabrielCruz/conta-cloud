INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion)
VALUES ('cuentas_cobrar.reportar','Generar reportes de cuentas por cobrar',
        'Generar cartera y antigüedad de saldos','VENTAS','cuentas_cobrar','reportar')
ON CONFLICT(codigo) DO NOTHING;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE upper(r.codigo)='ADMINISTRADOR' AND p.codigo='cuentas_cobrar.reportar'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;

CREATE INDEX idx_receipt_application_ar_history
    ON customer_receipt_application(tenant_id,empresa_id,accounts_receivable_id,applied_at);
