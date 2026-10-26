package com.citacloud.springboot.contacloud.app.migration;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class MovimientoCajaMigrationTest {
    @Test void creaCatalogoSecuenciaReversionesYPermisosAdministrativos() throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration/V90__implementar_movimientos_caja_pos.sql"));
        assertThat(sql).contains("cash_movement_concepts","cash_movement_sequence","reversal_of_movement_id",
            "uk_financial_movement_single_reversal","movimientos_caja.ver","movimientos_caja.crear",
            "movimientos_caja.revertir","upper(r.codigo)='ADMINISTRADOR'");
        assertThat(sql.toLowerCase()).doesNotContain("max(cash_movement_number)");
    }

    @Test void fortaleceLaRelacionDeReversionSinModificarV90() throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration/V91__fortalecer_integridad_reversion_movimiento_caja.sql"));
        assertThat(sql).contains("uk_financial_movement_tenant_company_id",
            "DROP CONSTRAINT fk_financial_movement_reversal",
            "FOREIGN KEY (tenant_id,empresa_id,reversal_of_movement_id)");
    }
}
