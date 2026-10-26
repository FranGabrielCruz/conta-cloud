package com.citacloud.springboot.contacloud.app.migration;
import org.junit.jupiter.api.Test; import java.nio.file.*; import static org.assertj.core.api.Assertions.*;
class RecuentoCajaMigrationTest {@Test void creaSnapshotsPermisosEIdempotencia() throws Exception {String sql=Files.readString(Path.of("src/main/resources/db/migration/V87__implementar_recuentos_caja.sql"));assertThat(sql).contains("cash_counts","cash_count_details","cash_denominations","recuentos_caja.crear","idempotency_key");assertThat(sql.toLowerCase()).doesNotContain("max(count_number)");}}
