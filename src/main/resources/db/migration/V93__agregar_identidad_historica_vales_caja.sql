ALTER TABLE cash_voucher
    ADD COLUMN company_name_snapshot VARCHAR(150),
    ADD COLUMN company_tax_id_snapshot VARCHAR(30),
    ADD COLUMN company_address_snapshot VARCHAR(255),
    ADD COLUMN company_phone_snapshot VARCHAR(40),
    ADD COLUMN company_email_snapshot VARCHAR(180),
    ADD COLUMN company_logo_object_key_snapshot VARCHAR(512);

UPDATE cash_voucher v
SET company_name_snapshot=COALESCE(NULLIF(TRIM(d.nombre_comercial),''),e.nombre),
    company_tax_id_snapshot=e.identificacion_fiscal,
    company_address_snapshot=d.direccion,
    company_phone_snapshot=d.telefono,
    company_email_snapshot=d.correo,
    company_logo_object_key_snapshot=e.logo_object_key
FROM empresas e
LEFT JOIN datos_empresa d ON d.empresa_id=e.id
WHERE e.tenant_id=v.tenant_id AND e.id=v.empresa_id;

ALTER TABLE cash_voucher
    ALTER COLUMN company_name_snapshot SET NOT NULL;

CREATE INDEX idx_cash_voucher_logo_snapshot
    ON cash_voucher(tenant_id,empresa_id,company_logo_object_key_snapshot)
    WHERE company_logo_object_key_snapshot IS NOT NULL;
