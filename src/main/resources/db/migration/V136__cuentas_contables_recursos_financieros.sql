ALTER TABLE cajas ADD COLUMN cuenta_contable_id UUID,
 ADD CONSTRAINT fk_caja_cuenta_contable FOREIGN KEY (tenant_id,empresa_id,cuenta_contable_id)
 REFERENCES cuenta_contable(tenant_id,empresa_id,id);
ALTER TABLE cuentas_bancarias ADD COLUMN cuenta_contable_id UUID,
 ADD CONSTRAINT fk_banco_cuenta_contable FOREIGN KEY (tenant_id,empresa_id,cuenta_contable_id)
 REFERENCES cuenta_contable(tenant_id,empresa_id,id);

