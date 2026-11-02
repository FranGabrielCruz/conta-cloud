package com.citacloud.springboot.contacloud.app.services;
import static org.assertj.core.api.Assertions.*;
import com.citacloud.springboot.contacloud.app.repositories.PaymentReceiptSnapshotRepository;
import com.citacloud.springboot.contacloud.app.dto.CobroClienteDto;
import com.citacloud.springboot.contacloud.app.models.EstadoReciboCliente;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ClassPathResource;

class PaymentReceiptSnapshotTest {
    UUID tenant=UUID.randomUUID(),company=UUID.randomUUID(),branch=UUID.randomUUID(),cash=UUID.randomUUID(),id=UUID.randomUUID();
    JdbcTemplate jdbc;PaymentReceiptSnapshotRepository snapshots;java.sql.Connection migrationConnection;
    @BeforeEach void schema() throws Exception {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:receipt-"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");jdbc=new JdbcTemplate(ds);snapshots=new PaymentReceiptSnapshotRepository(jdbc);
        jdbc.execute("CREATE TABLE empresas(id UUID,tenant_id UUID,nombre VARCHAR,identificacion_fiscal VARCHAR,pais_codigo VARCHAR,zona_horaria VARCHAR)");
        jdbc.execute("CREATE TABLE datos_empresa(empresa_id UUID,nombre_comercial VARCHAR,direccion VARCHAR,telefono VARCHAR)");
        jdbc.execute("CREATE TABLE cajas(tenant_id UUID,empresa_id UUID,sucursal_id UUID,id UUID,nombre VARCHAR,UNIQUE(tenant_id,empresa_id,sucursal_id,id))");
        jdbc.execute("CREATE TABLE customer_receipt(id UUID,tenant_id UUID,empresa_id UUID,branch_id UUID,status VARCHAR)");
        jdbc.execute("CREATE TABLE billing_print_jobs(document_type VARCHAR CONSTRAINT ck_billing_print_document_type CHECK(document_type IN ('SALES_INVOICE','SALES_RETURN')))");
        jdbc.execute("CREATE TABLE customer_receipt_payment(customer_receipt_id UUID,tenant_id UUID,empresa_id UUID,method_name VARCHAR,amount NUMERIC(19,2),reference VARCHAR,method_type VARCHAR,line_order INT)");
        migrationConnection=ds.getConnection();ScriptUtils.executeSqlScript(migrationConnection,new ClassPathResource("db/migration/V103__impresion_pos_recibos_cobro.sql"));
        jdbc.update("INSERT INTO empresas VALUES(?,?,?,?,?,?)",company,tenant,"DEMO","124543678","DO","America/Santo_Domingo");
        jdbc.update("INSERT INTO cajas VALUES(?,?,?,?,?)",tenant,company,branch,cash,"Caja 1");
        jdbc.update("INSERT INTO customer_receipt(id,tenant_id,empresa_id,branch_id,status) VALUES(?,?,?,?,?)",id,tenant,company,branch,"APPLIED");
        jdbc.update("INSERT INTO customer_receipt_payment VALUES(?,?,?,?,?,?,?,?)",id,tenant,company,"Transferencia",new BigDecimal("7500"),"TRX-123","BANK_TRANSFER",1);
    }
    @AfterEach void closeMigrationConnection() throws Exception {if(migrationConnection!=null)migrationConnection.close();}
    CobroClienteDto receipt(){return new CobroClienteDto(id,"REC-001",UUID.randomUUID(),"Juan Pérez","001-1234567-8",UUID.randomUUID(),"DOP",branch,"Principal",LocalDate.of(2026,11,1),OffsetDateTime.parse("2026-11-01T21:25:00Z"),new BigDecimal("7500"),new BigDecimal("7500"),BigDecimal.ZERO,EstadoReciboCliente.APPLIED,null,"Abono", "José",null,
        List.of(new CobroClienteDto.Aplicacion(UUID.randomUUID(),UUID.randomUUID(),"FAC-021",LocalDate.of(2026,10,1),OffsetDateTime.parse("2026-11-01T21:25:00Z"),new BigDecimal("10000"),new BigDecimal("7500"),new BigDecimal("2500"),false)),List.of());}
    @Test void capturaEsHistoricaInmutableYConZonaDeEmpresa(){
        snapshots.conservar(receipt(),tenant,company,cash,new BigDecimal("12500"),new BigDecimal("5000"));
        var first=snapshots.obtener(id,tenant,company);var d=snapshots.documento(first);
        assertThat(d.receiptDateTime()).isEqualTo(LocalDateTime.of(2026,11,1,17,25));assertThat(d.documents().getFirst().amount()).isEqualByComparingTo("7500");assertThat(d.previousBalance()).isEqualByComparingTo("12500");
        jdbc.update("UPDATE empresas SET nombre='NUEVO'");jdbc.update("UPDATE customer_receipt_payment SET amount=1,reference='CAMBIO'");
        snapshots.conservar(receipt(),tenant,company,cash,BigDecimal.ZERO,BigDecimal.ZERO);
        assertThat(snapshots.obtener(id,tenant,company).json()).isEqualTo(first.json());assertThat(snapshots.documento(first)).isEqualTo(d);
    }
    @Test void noCruzaTenantEmpresaNiSucursal(){assertThat(snapshots.obtener(id,UUID.randomUUID(),company)).isNull();assertThat(snapshots.obtener(id,tenant,UUID.randomUUID())).isNull();jdbc.update("UPDATE cajas SET sucursal_id=?",UUID.randomUUID());assertThatThrownBy(()->snapshots.conservar(receipt(),tenant,company,cash,null,null)).isInstanceOf(RecursoNoEncontradoException.class);}
    @Test void migracionAdmiteNuevoTipoYConservaAnteriores(){for(String t:List.of("SALES_INVOICE","SALES_RETURN","PAYMENT_RECEIPT"))jdbc.update("INSERT INTO billing_print_jobs VALUES(?)",t);assertThatThrownBy(()->jdbc.update("INSERT INTO billing_print_jobs VALUES('PDF')")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);}
    @Test void tarjetasYSecretosNoSePersistenEnSnapshot(){
        assertThat(PaymentReceiptSnapshotRepository.safeReference("4111111111111111","CREDIT_CARD")).isNull();assertThat(PaymentReceiptSnapshotRepository.safeReference("**** 4821","CREDIT_CARD")).isEqualTo("**** 4821");assertThat(PaymentReceiptSnapshotRepository.safeReference("CVV: 123","OTHER")).isNull();assertThat(PaymentReceiptSnapshotRepository.safeReference("token=abc","OTHER")).isNull();assertThat(PaymentReceiptSnapshotRepository.safeReference("TRX-123","BANK_TRANSFER")).isEqualTo("TRX-123");
    }
    @Test void legacyNoInventaDatosNiBalances(){var s=snapshots.obtener(id,tenant,company);assertThatThrownBy(()->snapshots.documento(s)).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("históricos");}
    @Test void rollbackNoDejaSnapshotYFalloPosteriorNoRevierteCobro(){
        var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        assertThatThrownBy(()->tx.execute(status->{snapshots.conservar(receipt(),tenant,company,cash,BigDecimal.TEN,BigDecimal.ZERO);throw new IllegalStateException("Confirmación abortada");})).isInstanceOf(IllegalStateException.class);
        assertThat(snapshots.obtener(id,tenant,company).json()).isNull();
        tx.executeWithoutResult(status->snapshots.conservar(receipt(),tenant,company,cash,BigDecimal.TEN,BigDecimal.ZERO));
        String committed=snapshots.obtener(id,tenant,company).json();
        var route=org.mockito.Mockito.mock(PrintAgentRoutingService.class);
        org.mockito.Mockito.when(route.resolver(cash)).thenThrow(new ReglaNegocioException("Agente no disponible"));
        var output=new PaymentReceiptPrintService(snapshots,new BillingPrintRouter(org.mockito.Mockito.mock(JdbcTemplate.class),route,org.mockito.Mockito.mock(com.citacloud.springboot.contacloud.app.repositories.AgenteImpresionRepository.class),org.mockito.Mockito.mock(AuditoriaService.class)));
        var principal=new com.citacloud.springboot.contacloud.app.security.TenantPrincipal(UUID.randomUUID(),tenant,company,null,"DEMO","Admin","admin","",true,true,Set.of(),Set.of("recibos.imprimir"));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        try{assertThatThrownBy(()->output.solicitar(id,UUID.randomUUID())).isInstanceOf(ReglaNegocioException.class);assertThat(snapshots.obtener(id,tenant,company).json()).isEqualTo(committed);}
        finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    }
    @Test void abonoGeneralConservaDisponibleSinInventarAplicacion(){
        var d=receipt();var advance=new CobroClienteDto(d.id(),d.numero(),d.clienteId(),d.cliente(),null,d.monedaId(),d.moneda(),d.sucursalId(),d.sucursal(),d.fecha(),d.confirmadoEn(),d.recibido(),BigDecimal.ZERO,d.recibido(),EstadoReciboCliente.AVAILABLE,null,"Abono general",d.usuario(),null,List.of(),List.of());
        snapshots.conservar(advance,tenant,company,cash,new BigDecimal("12500"),new BigDecimal("12500"));
        var doc=snapshots.documento(snapshots.obtener(id,tenant,company));assertThat(doc.documents()).isEmpty();assertThat(doc.totalApplied()).isZero();assertThat(doc.amountApplied()).isZero();assertThat(doc.totalCollected()).isEqualByComparingTo("7500");assertThat(doc.remainingBalance()).isEqualByComparingTo("12500");
    }
    @Test void capturasConcurrentesNoSobrescribenSnapshot() throws Exception {
        var barrier=new java.util.concurrent.CyclicBarrier(2);
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var a=workers.submit(()->{barrier.await();snapshots.conservar(receipt(),tenant,company,cash,BigDecimal.ONE,BigDecimal.ZERO);return true;});
            var c=workers.submit(()->{barrier.await();snapshots.conservar(receipt(),tenant,company,cash,BigDecimal.TEN,BigDecimal.ZERO);return true;});
            assertThat(a.get(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();assertThat(c.get(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
        String stored=snapshots.obtener(id,tenant,company).json();var doc=snapshots.documento(snapshots.obtener(id,tenant,company));assertThat(doc.previousBalance()).isIn(BigDecimal.ONE,BigDecimal.TEN);
        snapshots.conservar(receipt(),tenant,company,cash,BigDecimal.ZERO,BigDecimal.ZERO);assertThat(snapshots.obtener(id,tenant,company).json()).isEqualTo(stored);
    }

}
