CREATE UNIQUE INDEX IF NOT EXISTS uk_empresas_tenant_id_id ON empresas(tenant_id, id);

CREATE OR REPLACE FUNCTION contacloud_asignar_tenant_empresa()
RETURNS TRIGGER AS $$
DECLARE
    tenant_empresa UUID;
BEGIN
    SELECT tenant_id INTO tenant_empresa FROM empresas WHERE id = NEW.empresa_id;
    IF tenant_empresa IS NULL THEN
        RAISE EXCEPTION 'La empresa indicada no existe';
    END IF;
    IF NEW.tenant_id IS NULL THEN
        NEW.tenant_id := tenant_empresa;
    ELSIF NEW.tenant_id <> tenant_empresa THEN
        RAISE EXCEPTION 'El tenant no corresponde a la empresa indicada';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    tabla TEXT;
    tablas TEXT[] := ARRAY[
        'usuarios', 'roles', 'usuario_roles', 'rol_permisos', 'datos_empresa',
        'sucursales', 'monedas', 'tasas_cambio', 'impuestos',
        'tipos_comprobantes_fiscales', 'secuencias', 'condiciones_pago',
        'periodos_fiscales', 'configuracion_contable', 'auditoria',
        'usuario_empresa', 'empresa_modulos'
    ];
BEGIN
    FOREACH tabla IN ARRAY tablas LOOP
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS tenant_id UUID', tabla);
        EXECUTE format(
            'UPDATE %I t SET tenant_id=e.tenant_id FROM empresas e WHERE t.empresa_id=e.id AND t.tenant_id IS NULL',
            tabla);
        EXECUTE format('ALTER TABLE %I ALTER COLUMN tenant_id SET NOT NULL', tabla);
        EXECUTE format('DROP TRIGGER IF EXISTS %I ON %I', 'trg_' || tabla || '_tenant_empresa', tabla);
        EXECUTE format(
            'CREATE TRIGGER %I BEFORE INSERT OR UPDATE OF tenant_id,empresa_id ON %I FOR EACH ROW EXECUTE FUNCTION contacloud_asignar_tenant_empresa()',
            'trg_' || tabla || '_tenant_empresa', tabla);
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I(tenant_id,empresa_id)', 'idx_' || tabla || '_tenant_empresa', tabla);
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
             WHERE conname = 'fk_' || tabla || '_tenant_empresa'
        ) THEN
            EXECUTE format(
                'ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY(tenant_id,empresa_id) REFERENCES empresas(tenant_id,id)',
                tabla, 'fk_' || tabla || '_tenant_empresa');
        END IF;
    END LOOP;
END $$;
