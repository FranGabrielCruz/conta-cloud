package com.citacloud.springboot.contacloud.app.services;

import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import static org.assertj.core.api.Assertions.*;

class PrintOnScreenMigrationTest {
    @Test void renombrarConservaValoresDefaultYUnaSolaColumna() throws Exception {
        try(var c=DriverManager.getConnection("jdbc:h2:mem:rename-"+UUID.randomUUID()+";MODE=PostgreSQL","sa","");var s=c.createStatement()){
            for(String tabla:new String[]{"print_agents","billing_print_jobs"}){
                s.execute("create table "+tabla+"(id int primary key,show_pdf_on_print boolean not null default true)");
                s.execute("insert into "+tabla+" values(1,true),(2,false)");
            }
            ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration/V102__renombrar_impresion_por_pantalla.sql"));
            for(String tabla:new String[]{"print_agents","billing_print_jobs"}){
                try(var r=s.executeQuery("select print_on_screen from "+tabla+" order by id")){
                    assertThat(r.next()).isTrue();assertThat(r.getBoolean(1)).isTrue();assertThat(r.next()).isTrue();assertThat(r.getBoolean(1)).isFalse();assertThat(r.next()).isFalse();
                }
                s.execute("insert into "+tabla+"(id) values(3)");
                try(var r=s.executeQuery("select print_on_screen from "+tabla+" where id=3")){r.next();assertThat(r.getBoolean(1)).isTrue();}
                assertThatThrownBy(()->s.executeQuery("select show_pdf_on_print from "+tabla)).isInstanceOf(SQLException.class);
            }
        }
    }
}
