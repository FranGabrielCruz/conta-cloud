package com.citacloud.springboot.contacloud.app.services;
import java.nio.file.*;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class CashVoucherSnapshotMigrationTest {
 @Test void migrationAddsOptionalSnapshotAndPreservesExistingVoucher()throws Exception{
  try(var c=DriverManager.getConnection("jdbc:h2:mem:voucher_migration;MODE=PostgreSQL");var s=c.createStatement()){
   s.execute("create table cash_voucher(id int primary key,voucher_number varchar(30),amount numeric(19,4))");s.execute("insert into cash_voucher values(1,'VAL-000025',2500)");s.execute(Files.readString(Path.of("src/main/resources/db/migration/V107__snapshot_pos_vales_caja.sql")));
   try(var rows=s.executeQuery("select voucher_number,amount,pos_print_snapshot from cash_voucher")){assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("VAL-000025");assertThat(rows.getBigDecimal(2)).isEqualByComparingTo("2500");assertThat(rows.getString(3)).isNull();}
  }
 }
}
