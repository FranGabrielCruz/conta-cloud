package com.citacloud.springboot.contacloud.app.services;

import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import static org.junit.jupiter.api.Assertions.*;

class SalesReturnSchemaIntegrationTest {
    @Test void exactMigrationKeepsLegacyJobsAndEnforcesSemanticTypesAndRegisterScope() throws Exception {
        try(var connection=DriverManager.getConnection("jdbc:h2:mem:return-schema-"+UUID.randomUUID()+";MODE=PostgreSQL","sa","");var sql=connection.createStatement()) {
            sql.execute("create table cajas(tenant_id uuid,empresa_id uuid,sucursal_id uuid,id uuid,primary key(tenant_id,empresa_id,sucursal_id,id))");
            sql.execute("create table sales_return(id uuid primary key,tenant_id uuid,empresa_id uuid)");
            sql.execute("create table sales_return_line(id uuid primary key)");sql.execute("create table billing_print_jobs(id uuid primary key)");
            UUID old=UUID.randomUUID();sql.executeUpdate("insert into billing_print_jobs values('"+old+"')");
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V101__impresion_pos_devoluciones_ventas.sql"));
            try(var result=sql.executeQuery("select document_type from billing_print_jobs where id='"+old+"'")){assertTrue(result.next());assertEquals("SALES_INVOICE",result.getString(1));}
            sql.executeUpdate("insert into billing_print_jobs(id,document_type) values('"+UUID.randomUUID()+"','SALES_RETURN')");
            assertThrows(SQLException.class,()->sql.executeUpdate("insert into billing_print_jobs(id,document_type) values('"+UUID.randomUUID()+"','PDF')"));
            UUID tenant=UUID.randomUUID(),company=UUID.randomUUID(),branch=UUID.randomUUID(),cash=UUID.randomUUID();
            sql.executeUpdate("insert into cajas values('"+tenant+"','"+company+"','"+branch+"','"+cash+"')");
            sql.executeUpdate("insert into sales_return(id,tenant_id,empresa_id,pos_branch_id,pos_cash_register_id) values('"+UUID.randomUUID()+"','"+tenant+"','"+company+"','"+branch+"','"+cash+"')");
            assertThrows(SQLException.class,()->sql.executeUpdate("insert into sales_return(id,tenant_id,empresa_id,pos_branch_id,pos_cash_register_id) values('"+UUID.randomUUID()+"','"+tenant+"','"+UUID.randomUUID()+"','"+branch+"','"+cash+"')"));
            assertThrows(SQLException.class,()->sql.executeUpdate("insert into sales_return(id,tenant_id,empresa_id,pos_branch_id) values('"+UUID.randomUUID()+"','"+tenant+"','"+company+"','"+branch+"')"));
            sql.executeUpdate("insert into sales_return(id,tenant_id,empresa_id) values('"+UUID.randomUUID()+"','"+tenant+"','"+company+"')");
        }
    }
}
