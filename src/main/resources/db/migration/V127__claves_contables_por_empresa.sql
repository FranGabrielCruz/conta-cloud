CREATE TABLE cuenta_contable_plantilla_clave (
    version INTEGER NOT NULL,
    clave VARCHAR(60) NOT NULL,
    codigo VARCHAR(40) NOT NULL,
    PRIMARY KEY (version,clave),
    FOREIGN KEY (version,codigo) REFERENCES cuenta_contable_plantilla_item(version,codigo)
);

INSERT INTO cuenta_contable_plantilla_clave VALUES
    (1,'ASSETS_ROOT','1'),
    (1,'LIABILITIES_ROOT','2'),
    (1,'EQUITY_ROOT','3'),
    (1,'REVENUE_ROOT','4'),
    (1,'COSTS_ROOT','5'),
    (1,'EXPENSES_ROOT','6'),
    (1,'CASH_DEFAULT','1.1.01.01'),
    (1,'BANK_DEFAULT','1.1.01.02'),
    (1,'ACCOUNTS_RECEIVABLE_DEFAULT','1.1.02.01'),
    (1,'INVENTORY_DEFAULT','1.1.03.01'),
    (1,'ACCOUNTS_PAYABLE_DEFAULT','2.1.01.01'),
    (1,'SALES_REVENUE_DEFAULT','4.1.01.01'),
    (1,'COGS_DEFAULT','5.1.01.01'),
    (1,'DEPRECIATION_EXPENSE_DEFAULT','6.1.04.01'),
    (1,'GAIN_ASSET_DISPOSAL_DEFAULT','4.2.01.01'),
    (1,'LOSS_ASSET_DISPOSAL_DEFAULT','6.2.01.01');

CREATE TABLE cuenta_contable_clave_empresa (
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    clave VARCHAR(60) NOT NULL,
    cuenta_id UUID NOT NULL,
    PRIMARY KEY (tenant_id,empresa_id,clave),
    FOREIGN KEY (tenant_id,empresa_id,cuenta_id)
        REFERENCES cuenta_contable(tenant_id,empresa_id,id) ON DELETE CASCADE
);

CREATE FUNCTION instalar_claves_contables(p_tenant UUID,p_empresa UUID,p_version INTEGER)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO cuenta_contable_clave_empresa(tenant_id,empresa_id,clave,cuenta_id)
    SELECT p_tenant,p_empresa,t.clave,c.id
    FROM cuenta_contable_plantilla_clave t
    JOIN cuenta_contable c ON c.tenant_id=p_tenant AND c.empresa_id=p_empresa AND c.codigo=t.codigo
    WHERE t.version=p_version AND c.creado_por IS NULL
    ON CONFLICT(tenant_id,empresa_id,clave) DO NOTHING;
END;
$$;

CREATE FUNCTION instalar_claves_catalogo_empresa() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM instalar_claves_contables(NEW.tenant_id,NEW.empresa_id,NEW.version);
    IF EXISTS (
        SELECT 1 FROM cuenta_contable_plantilla_clave t
        WHERE t.version=NEW.version AND NOT EXISTS (
            SELECT 1 FROM cuenta_contable_clave_empresa c
            WHERE c.tenant_id=NEW.tenant_id AND c.empresa_id=NEW.empresa_id AND c.clave=t.clave
        )
    ) THEN
        RAISE EXCEPTION 'No se pudieron asignar las claves de la plantilla contable.';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_instalacion_catalogo_claves
AFTER INSERT OR UPDATE ON cuenta_contable_instalacion
FOR EACH ROW EXECUTE FUNCTION instalar_claves_catalogo_empresa();

SELECT instalar_claves_contables(tenant_id,empresa_id,version) FROM cuenta_contable_instalacion;

DO $$
DECLARE
    empresa RECORD;
BEGIN
    FOR empresa IN
        SELECT e.tenant_id,e.id FROM empresas e
        WHERE NOT EXISTS (
            SELECT 1 FROM cuenta_contable c WHERE c.tenant_id=e.tenant_id AND c.empresa_id=e.id
        )
        ORDER BY e.tenant_id,e.id
    LOOP
        PERFORM instalar_catalogo_contable(empresa.tenant_id,empresa.id);
    END LOOP;
END;
$$;
