-- Phase 2: V99 was validated against PostgreSQL before retiring the legacy representation.
DO $$ BEGIN
    IF EXISTS(SELECT 1 FROM print_agents a WHERE a.cash_register_id IS NOT NULL AND NOT EXISTS(
        SELECT 1 FROM print_agent_cash_registers ar
        WHERE ar.tenant_id=a.tenant_id AND ar.empresa_id=a.empresa_id AND ar.sucursal_id=a.sucursal_id
          AND ar.agent_id=a.id AND ar.cash_register_id=a.cash_register_id)) THEN
        RAISE EXCEPTION 'No se puede retirar la caja anterior: existen asociaciones sin migrar.';
    END IF;
END $$;
ALTER TABLE print_agents DROP CONSTRAINT fk_print_agent_caja;
DROP INDEX idx_print_agent_caja;
ALTER TABLE print_agents DROP COLUMN cash_register_id;
