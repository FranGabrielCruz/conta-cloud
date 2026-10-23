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
}
