package com.citacloud.springboot.contacloud.app.services;
import java.nio.file.*;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class CashClosingSnapshotMigrationTest {
 @Test void migrationAddsOptionalSnapshotAndPreservesExistingVoucher()throws Exception{
  try(var c=DriverManager.getConnection("jdbc:h2:mem:closing_migration;MODE=PostgreSQL");var s=c.createStatement()){
   s.execute("create table cash_closures(id int primary key,closure_number varchar(30),amount numeric(19,4))");s.execute("insert into cash_closures values(1,'CIE-000018',2500)");s.execute(Files.readString(Path.of("src/main/resources/db/migration/V108__snapshot_pos_cierres_caja.sql")));
   try(var rows=s.executeQuery("select closure_number,amount,pos_print_snapshot from cash_closures")){assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("CIE-000018");assertThat(rows.getBigDecimal(2)).isEqualByComparingTo("2500");assertThat(rows.getString(3)).isNull();}
  }
 }
}
