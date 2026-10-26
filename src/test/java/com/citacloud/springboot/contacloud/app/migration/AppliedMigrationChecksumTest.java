package com.citacloud.springboot.contacloud.app.migration;

import org.flywaydb.core.internal.resolver.ChecksumCalculator;
import org.flywaydb.core.internal.resource.StringResource;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppliedMigrationChecksumTest {
    @Test void mantieneIntactaLaMigracionV81YaAplicada() throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration/V81__implementar_devoluciones_ventas.sql"));
        assertEquals(1516611455,ChecksumCalculator.calculate(new StringResource(sql)),
                "V81 ya fue aplicada; cualquier cambio posterior debe ir en una migración nueva");
    }

    @Test void mantieneIntactaLaMigracionV84YaAplicada() throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration/V84__implementar_recibos_cobros_clientes.sql"));
        assertEquals(-695281857,ChecksumCalculator.calculate(new StringResource(sql)),
                "V84 ya fue aplicada; los cambios de sucursal deben permanecer en V85");
    }

    @Test void mantieneIntactaLaMigracionV87YaAplicada() throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration/V87__implementar_recuentos_caja.sql"));
        assertEquals(-460316646,ChecksumCalculator.calculate(new StringResource(sql)),
                "V87 ya fue aplicada; cualquier ajuste posterior debe permanecer en V88 o una migración nueva");
    }

    @Test void mantieneIntactaLaMigracionV90YaAplicada() throws Exception {
        String sql=Files.readString(Path.of("src/main/resources/db/migration/V90__implementar_movimientos_caja_pos.sql"));
        assertEquals(-207108717,ChecksumCalculator.calculate(new StringResource(sql)),
                "V90 ya fue aplicada; cualquier ajuste posterior debe permanecer en V91 o una migración nueva");
    }
}
