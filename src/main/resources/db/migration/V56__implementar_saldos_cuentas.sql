CREATE INDEX idx_movimientos_saldo_banco
    ON movimientos_financieros(tenant_id,empresa_id,cuenta_bancaria_id,fecha,creado_en,id)
    WHERE tipo_cuenta='BANK_ACCOUNT';

CREATE INDEX idx_movimientos_saldo_caja
    ON movimientos_financieros(tenant_id,empresa_id,caja_id,fecha,creado_en,id)
    WHERE tipo_cuenta='CASH_REGISTER';

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion) VALUES
('saldos_cuentas.ver','Ver saldos de cuentas','Consultar saldos de cajas y cuentas bancarias','CAJA_BANCOS','saldos_cuentas','ver'),
('saldos_cuentas.ver_movimientos','Ver movimientos de saldos','Consultar el detalle cronológico de movimientos','CAJA_BANCOS','saldos_cuentas','ver_movimientos'),
('saldos_cuentas.ver_bancaria','Ver información bancaria en saldos','Consultar datos bancarios enmascarados','CAJA_BANCOS','saldos_cuentas','ver_bancaria')
ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre,descripcion=EXCLUDED.descripcion,
    modulo=EXCLUDED.modulo,recurso=EXCLUDED.recurso,accion=EXCLUDED.accion;

INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE upper(r.codigo)='ADMINISTRADOR' AND p.recurso='saldos_cuentas'
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
