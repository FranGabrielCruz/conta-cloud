package com.citacloud.springboot.contacloud.app.services;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class CashCountMigrationTest {
    @Test void agregaSnapshotSinCambiarRecuentosYConservaTiposAnteriores() throws Exception {
        try(var c=DriverManager.getConnection("jdbc:h2:mem:count-"+UUID.randomUUID()+";MODE=PostgreSQL","sa","");var s=c.createStatement()){
            s.execute("CREATE TABLE cash_counts(id int primary key,count_number varchar,expected_amount numeric(19,4),counted_amount numeric(19,4))");s.execute("INSERT INTO cash_counts VALUES(1,'RCJ-000001',100,95)");
            s.execute("CREATE TABLE billing_print_jobs(document_type varchar,CONSTRAINT ck_billing_print_document_type CHECK(document_type IN ('SALES_INVOICE','SALES_RETURN','PAYMENT_RECEIPT')))");
            for(String type:new String[]{"SALES_INVOICE","SALES_RETURN","PAYMENT_RECEIPT"})s.execute("INSERT INTO billing_print_jobs VALUES('"+type+"')");
            ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration/V104__impresion_pos_recuentos_caja.sql"));s.execute("INSERT INTO billing_print_jobs VALUES('CASH_COUNT')");assertThatThrownBy(()->s.execute("INSERT INTO billing_print_jobs VALUES('PDF')")).isInstanceOf(SQLException.class);
            try(var r=s.executeQuery("SELECT count_number,expected_amount,counted_amount,pos_print_snapshot FROM cash_counts")){assertThat(r.next()).isTrue();assertThat(r.getString(1)).isEqualTo("RCJ-000001");assertThat(r.getBigDecimal(2)).isEqualByComparingTo("100");assertThat(r.getBigDecimal(3)).isEqualByComparingTo("95");assertThat(r.getString(4)).isNull();}
        }
    }
}
