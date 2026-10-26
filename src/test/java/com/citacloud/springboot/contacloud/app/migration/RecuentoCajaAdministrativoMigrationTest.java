package com.citacloud.springboot.contacloud.app.migration;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class RecuentoCajaAdministrativoMigrationTest {
    @Test void muevePermisosExistentesACajaYBancosSinModificarMigracionAplicada() throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration/V89__ubicar_permisos_recuento_en_caja_bancos.sql"));
        assertThat(sql).contains("UPDATE permisos","CAJA_BANCOS","recuentos_caja.ver","recuentos_caja.imprimir");
    }
}
