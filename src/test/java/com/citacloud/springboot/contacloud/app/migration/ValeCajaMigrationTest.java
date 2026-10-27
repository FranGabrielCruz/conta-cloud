package com.citacloud.springboot.contacloud.app.migration;
import org.junit.jupiter.api.Test;import java.nio.file.*;import static org.assertj.core.api.Assertions.*;
class ValeCajaMigrationTest {
 @Test void creaValesSecuenciasIntegridadYPermisos() throws Exception {String sql=Files.readString(Path.of("src/main/resources/db/migration/V92__implementar_vales_caja.sql"));assertThat(sql).contains("cash_voucher_concepts","cash_voucher_sequence","CREATE TABLE cash_voucher","CASH_VOUCHER","vales_caja.ver","vales_caja.crear","vales_caja.imprimir","vales_caja.anular","upper(r.codigo)='ADMINISTRADOR'","ck_cash_voucher_void");assertThat(sql.toLowerCase()).doesNotContain("max(voucher_number)");}
 @Test void conservaLaIdentidadHistoricaUsadaPorElComprobante() throws Exception {String sql=Files.readString(Path.of("src/main/resources/db/migration/V93__agregar_identidad_historica_vales_caja.sql"));assertThat(sql).contains("company_name_snapshot","company_tax_id_snapshot","company_address_snapshot","company_phone_snapshot","company_email_snapshot","company_logo_object_key_snapshot","SET NOT NULL");}
}
