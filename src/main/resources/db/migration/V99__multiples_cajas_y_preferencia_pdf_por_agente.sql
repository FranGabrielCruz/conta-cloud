-- Preserve legacy columns and settings until the migrated associations have been validated.
ALTER TABLE print_agents ADD COLUMN show_pdf_on_print BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE print_agents a SET show_pdf_on_print=s.show_pdf_on_print
FROM billing_print_settings s WHERE s.tenant_id=a.tenant_id AND s.empresa_id=a.empresa_id;

ALTER TABLE print_agents ADD CONSTRAINT uk_print_agent_branch_context UNIQUE(tenant_id,empresa_id,sucursal_id,id);
ALTER TABLE print_agents ADD CONSTRAINT uk_print_agent_assignment_state UNIQUE(tenant_id,empresa_id,sucursal_id,id,status);

DO $$ BEGIN
    IF EXISTS(SELECT 1 FROM print_agents WHERE status='ACTIVE' AND cash_register_id IS NOT NULL
        GROUP BY tenant_id,empresa_id,cash_register_id HAVING count(*)>1) THEN
        RAISE EXCEPTION 'Existen cajas asignadas a varios agentes activos. Resuelva esas asignaciones antes de migrar.';
    END IF;
END $$;

CREATE TABLE print_agent_cash_registers (
    tenant_id UUID NOT NULL,
    empresa_id UUID NOT NULL,
    sucursal_id UUID NOT NULL,
    agent_id UUID NOT NULL,
    cash_register_id UUID NOT NULL,
    agent_status VARCHAR(20) NOT NULL,
    PRIMARY KEY(agent_id,cash_register_id),
    CONSTRAINT fk_print_agent_register_agent FOREIGN KEY(tenant_id,empresa_id,sucursal_id,agent_id,agent_status)
        REFERENCES print_agents(tenant_id,empresa_id,sucursal_id,id,status) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_print_agent_register_cash_register FOREIGN KEY(tenant_id,empresa_id,sucursal_id,cash_register_id)
        REFERENCES cajas(tenant_id,empresa_id,sucursal_id,id)
);

-- The mirrored state is controlled by the parent FK and this trigger, never by a client.
-- ON UPDATE CASCADE releases/reclaims the partial unique index atomically on revocation/activation.
CREATE FUNCTION sincronizar_estado_caja_agente() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    SELECT status INTO NEW.agent_status FROM print_agents
    WHERE tenant_id=NEW.tenant_id AND empresa_id=NEW.empresa_id AND sucursal_id=NEW.sucursal_id AND id=NEW.agent_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE='23503', MESSAGE='El agente no pertenece al tenant, empresa y sucursal de la asignación.';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_print_agent_register_state BEFORE INSERT OR UPDATE ON print_agent_cash_registers
    FOR EACH ROW EXECUTE FUNCTION sincronizar_estado_caja_agente();

INSERT INTO print_agent_cash_registers(tenant_id,empresa_id,sucursal_id,agent_id,cash_register_id)
SELECT tenant_id,empresa_id,sucursal_id,id,cash_register_id FROM print_agents WHERE cash_register_id IS NOT NULL;

CREATE UNIQUE INDEX uk_print_agent_active_register ON print_agent_cash_registers(tenant_id,empresa_id,cash_register_id)
    WHERE agent_status='ACTIVE';
CREATE INDEX idx_print_agent_register_lookup ON print_agent_cash_registers(tenant_id,empresa_id,cash_register_id,agent_id);

ALTER TABLE billing_print_jobs ADD COLUMN show_pdf_on_print BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE billing_print_jobs j SET show_pdf_on_print=a.show_pdf_on_print
FROM print_agents a WHERE a.tenant_id=j.tenant_id AND a.empresa_id=j.empresa_id AND a.id=j.agent_id;

INSERT INTO permisos(codigo,nombre,descripcion,modulo,recurso,accion)
VALUES('agentes_impresion.editar','Editar agentes de impresión','Modificar cajas y configuración del agente','CONFIGURACION','agentes_impresion','editar')
ON CONFLICT(codigo) DO NOTHING;
INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id)
SELECT r.empresa_id,r.id,p.id FROM roles r CROSS JOIN permisos p
WHERE p.codigo='agentes_impresion.editar' AND (r.codigo='ADMINISTRADOR' OR EXISTS(
    SELECT 1 FROM rol_permisos rp JOIN permisos anterior ON anterior.id=rp.permiso_id
    WHERE rp.rol_id=r.id AND anterior.codigo='agentes_impresion.crear'))
ON CONFLICT(rol_id,permiso_id) DO NOTHING;
