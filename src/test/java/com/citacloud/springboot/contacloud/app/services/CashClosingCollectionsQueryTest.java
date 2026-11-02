package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.repositories.FinancialMovementRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class CashClosingCollectionsQueryTest {
 @Test void officialAggregationIncludesNoncashLinesOnlyFromTheScopedSessionAndExcludesManualCash()throws Exception{
  UUID t=UUID.randomUUID(),e=UUID.randomUUID(),session=UUID.randomUUID(),other=UUID.randomUUID();
  try(var connection=DriverManager.getConnection("jdbc:h2:mem:closing_collections;MODE=PostgreSQL")){
   var jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
   jdbc.execute("create table sales_invoice(id uuid,tenant_id uuid,empresa_id uuid,status varchar,cash_register_session_id uuid)");
   jdbc.execute("create table sales_payment(id uuid,sales_invoice_id uuid,tenant_id uuid,empresa_id uuid,cash_register_session_id uuid)");
   jdbc.execute("create table sales_payment_application(id uuid,sales_payment_id uuid,tenant_id uuid,empresa_id uuid,payment_method_id uuid,method_name varchar,amount numeric(19,4))");
   jdbc.execute("create table customer_receipt(id uuid,tenant_id uuid,empresa_id uuid,status varchar)");
   jdbc.execute("create table customer_receipt_payment(id uuid,tenant_id uuid,empresa_id uuid,customer_receipt_id uuid,payment_method_id uuid,method_name varchar,amount numeric(19,4),cash_register_session_id uuid,financial_movement_id uuid)");
   jdbc.execute("create table movimientos_financieros(id uuid,tenant_id uuid,empresa_id uuid,cash_register_session_id uuid,estado varchar,tipo_movimiento varchar,tipo_origen varchar,payment_method varchar,monto numeric(19,4),origen_id uuid)");
   UUID cash=UUID.randomUUID(),card=UUID.randomUUID(),transfer=UUID.randomUUID();
   sale(jdbc,t,e,session,cash,card,transfer,"CONFIRMED");sale(jdbc,t,e,other,cash,card,transfer,"CONFIRMED");sale(jdbc,UUID.randomUUID(),e,session,cash,card,transfer,"CONFIRMED");sale(jdbc,t,UUID.randomUUID(),session,cash,card,transfer,"CONFIRMED");sale(jdbc,t,e,session,cash,card,transfer,"VOIDED");
   receipt(jdbc,t,e,session,cash,card,"APPLIED");receipt(jdbc,t,e,other,cash,card,"APPLIED");receipt(jdbc,t,e,session,cash,card,"VOIDED");
   jdbc.update("insert into movimientos_financieros values(?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),t,e,session,"REGISTERED","INCOME","MANUAL","CASH",new BigDecimal("9999"),null);
   String sql=FinancialMovementRepository.class.getMethod("formasPagoSesion",UUID.class,UUID.class,UUID.class).getAnnotation(Query.class).value();
   var result=new NamedParameterJdbcTemplate(jdbc).query(sql,Map.of("tenant",t,"empresa",e,"sesion",session),(rs,n)->Map.entry(rs.getString("nombre"),rs.getBigDecimal("importe")));
   assertThat(result).hasSize(3);var amounts=new HashMap<String,BigDecimal>();result.forEach(x->amounts.put(x.getKey(),x.getValue()));assertThat(amounts.get("Efectivo histórico")).isEqualByComparingTo("110");assertThat(amounts.get("Tarjeta histórica")).isEqualByComparingTo("55");assertThat(amounts.get("Transferencia histórica")).isEqualByComparingTo("25");assertThat(amounts.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("190");
  }
 }
 void sale(JdbcTemplate j,UUID t,UUID e,UUID session,UUID cash,UUID card,UUID transfer,String status){UUID invoice=UUID.randomUUID(),payment=UUID.randomUUID();j.update("insert into sales_invoice values(?,?,?,?,?)",invoice,t,e,status,session);j.update("insert into sales_payment values(?,?,?,?,?)",payment,invoice,t,e,null);for(var row:List.of(Map.entry(cash,"Efectivo histórico"),Map.entry(card,"Tarjeta histórica"),Map.entry(transfer,"Transferencia histórica"))){String amount=row.getKey().equals(cash)?"100":row.getKey().equals(card)?"50":"25";j.update("insert into sales_payment_application values(?,?,?,?,?,?,?)",UUID.randomUUID(),payment,t,e,row.getKey(),row.getValue(),new BigDecimal(amount));}}
 void receipt(JdbcTemplate j,UUID t,UUID e,UUID session,UUID cash,UUID card,String status){UUID receipt=UUID.randomUUID();j.update("insert into customer_receipt values(?,?,?,?)",receipt,t,e,status);j.update("insert into customer_receipt_payment values(?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),t,e,receipt,cash,"Efectivo histórico",BigDecimal.TEN,session,null);j.update("insert into customer_receipt_payment values(?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),t,e,receipt,card,"Tarjeta histórica",new BigDecimal("5"),null,null);}
}
