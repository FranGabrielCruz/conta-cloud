package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.security.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
/** Real PostgreSQL, actual repository locks, isolated removable fixtures. */
@EnabledIfEnvironmentVariable(named="CONTACLOUD_POSTGRES_TESTS",matches="true")
class ReliablePrintQueuePostgresTest {
 PrintAgentPostgresIntegrationTest f; BillingPrintService service; TransactionTemplate tx;
 @BeforeAll static void setup() throws Exception {
  PrintAgentPostgresIntegrationTest.conectarYValidarModelo();
  org.flywaydb.core.Flyway.configure().dataSource(PrintAgentPostgresIntegrationTest.ds).locations("filesystem:src/main/resources/db/migration").load().migrate();
 }
 @AfterAll static void close(){PrintAgentPostgresIntegrationTest.cerrar();}
 @BeforeEach void fixture(){f=new PrintAgentPostgresIntegrationTest();f.preparar();f.asignar(f.a,f.cajas.getFirst());
  var em=org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(PrintAgentPostgresIntegrationTest.factory.getObject());
  var agents=new JpaRepositoryFactory(em).getRepository(AgenteImpresionRepository.class);
  service=new BillingPrintService(f.jdbc,mock(FacturaVentaRepository.class),mock(PrintAgentRoutingService.class),mock(FacturaVentaPdfService.class),agents,mock(AuditoriaService.class));
  service.configureQueuePolicy(new PrintJobRetryPolicy(2,30,1,1,0));
  tx=new TransactionTemplate(new org.springframework.orm.jpa.JpaTransactionManager(PrintAgentPostgresIntegrationTest.factory.getObject()));
 }
 @AfterEach void cleanup(){if(f!=null)f.limpiar();}
 UUID job(){return f.trabajo(f.a,f.cajas.getFirst());}
 TrabajoImpresionDto claim(){return tx.execute(t->service.siguiente(f.principal(f.a)).orElseThrow());}
 void start(TrabajoImpresionDto j){tx.executeWithoutResult(t->service.renovarReserva(f.principal(f.a),j.id(),j.claimId(),true));}
 void result(TrabajoImpresionDto j,boolean success,String code){tx.executeWithoutResult(t->service.resultado(f.principal(f.a),j.id(),j.claimId(),success,code));}
 String status(UUID id){return f.jdbc.queryForObject("select status from billing_print_jobs where id=? and tenant_id=? and empresa_id=?",String.class,id,PrintAgentPostgresIntegrationTest.tenant,PrintAgentPostgresIntegrationTest.empresa);}
 void expire(UUID id){f.jdbc.update("update billing_print_jobs set lease_until=current_timestamp-interval '1 second',available_at=current_timestamp where id=? and agent_id=?",id,f.a);}
 void available(UUID id){f.jdbc.update("update billing_print_jobs set available_at=current_timestamp where id=? and agent_id=?",id,f.a);}
 @Test void transientRetryRetainsJobAndChangesClaimThenCompletesIdempotently(){UUID id=job();var a=claim();assertThat(status(id)).isEqualTo("PROCESSING");result(a,false,"PRINTER_UNAVAILABLE");assertThat(status(id)).isEqualTo("PENDING");result(a,false,"PRINTER_UNAVAILABLE");assertThat(tx.<Optional<TrabajoImpresionDto>>execute(t->service.siguiente(f.principal(f.a)))).isEmpty();available(id);var b=claim();assertThat(b.id()).isEqualTo(id);assertThat(b.claimId()).isNotEqualTo(a.claimId());assertThat(b.attempt()).isEqualTo(2);start(b);result(b,true,null);result(b,true,null);assertThat(status(id)).isEqualTo("PRINTED");}
 @Test void permanentFailureIsNeverReclaimed(){UUID id=job();var a=claim();result(a,false,"INVALID_SNAPSHOT");assertThat(status(id)).isEqualTo("FAILED");available(id);assertThat(tx.<Optional<TrabajoImpresionDto>>execute(t->service.siguiente(f.principal(f.a)))).isEmpty();}
 @Test void exhaustedTransientFailureBecomesTerminal(){UUID id=job();var a=claim();result(a,false,"TIMEOUT");available(id);var b=claim();result(b,false,"TIMEOUT");assertThat(status(id)).isEqualTo("FAILED");assertThat(f.jdbc.queryForObject("select completed_at is not null from billing_print_jobs where id=?",Boolean.class,id)).isTrue();}
 @Test void expiredBeforeOutputRecoversAndRejectsOldClaim(){UUID id=job();var a=claim();expire(id);var b=claim();assertThat(b.id()).isEqualTo(id);assertThatThrownBy(()->start(a)).isInstanceOf(ReglaNegocioException.class);start(b);result(b,true,null);assertThat(status(id)).isEqualTo("PRINTED");}
 @Test void startedOutputExpiryIsUncertainAndLateDurableSuccessReconciles(){UUID id=job();var a=claim();start(a);expire(id);assertThat(tx.<Optional<TrabajoImpresionDto>>execute(t->service.siguiente(f.principal(f.a)))).isEmpty();assertThat(status(id)).isEqualTo("FAILED");assertThat(f.jdbc.queryForObject("select last_error_code from billing_print_jobs where id=?",String.class,id)).isEqualTo("OUTPUT_UNCERTAIN");result(a,true,null);result(a,true,null);assertThat(status(id)).isEqualTo("PRINTED");}
 @Test void renewalExtendsLeaseAndSuccessNeedsOutputGate(){job();var a=claim();assertThatThrownBy(()->result(a,true,null)).isInstanceOf(ReglaNegocioException.class);tx.executeWithoutResult(t->service.renovarReserva(f.principal(f.a),a.id(),a.claimId(),false));assertThat(f.jdbc.queryForObject("select lease_until>current_timestamp from billing_print_jobs where id=?",Boolean.class,a.id())).isTrue();start(a);result(a,true,null);}
 @Test void removedCashRegisterCannotStartButStartedOutputCanFinish(){job();var a=claim();f.jdbc.update("delete from print_agent_cash_registers where agent_id=?",f.a);assertThatThrownBy(()->start(a)).isInstanceOf(ReglaNegocioException.class);f.asignar(f.a,f.cajas.getFirst());start(a);f.jdbc.update("delete from print_agent_cash_registers where agent_id=?",f.a);result(a,true,null);assertThat(status(a.id())).isEqualTo("PRINTED");}
 @Test void revokedAgentWrongOwnerAndWrongTenantCannotReport(){job();var a=claim();assertThatThrownBy(()->tx.executeWithoutResult(t->service.resultado(f.principal(f.b),a.id(),a.claimId(),false,"TIMEOUT"))).isInstanceOf(RecursoNoEncontradoException.class);var wrong=new PrintAgentPrincipal(f.a,UUID.randomUUID(),PrintAgentPostgresIntegrationTest.empresa,PrintAgentPostgresIntegrationTest.sucursal,"TEST","Test");assertThatThrownBy(()->tx.executeWithoutResult(t->service.resultado(wrong,a.id(),a.claimId(),false,"TIMEOUT"))).isInstanceOf(VinculacionAgenteException.class);f.jdbc.update("update print_agents set status='REVOKED' where id=?",f.a);assertThatThrownBy(()->start(a)).isInstanceOf(VinculacionAgenteException.class);}
 @Test void concurrentWorkersClaimOnlyOnce() throws Exception {UUID id=job();var go=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)){var tasks=new ArrayList<Future<Optional<TrabajoImpresionDto>>>();for(int i=0;i<2;i++)tasks.add(pool.submit(()->{go.await();return tx.execute(t->service.siguiente(f.principal(f.a)));}));go.countDown();int count=0;for(var task:tasks)if(task.get(15,TimeUnit.SECONDS).isPresent())count++;assertThat(count).isEqualTo(1);}assertThat(status(id)).isEqualTo("PROCESSING");}
 @Test void legacyExpiredProcessingNeverAutomaticallyPrintsAgain(){UUID id=job();tx.execute(t->service.siguiente(f.principal(f.a),false).orElseThrow());expire(id);assertThat(tx.<Optional<TrabajoImpresionDto>>execute(t->service.siguiente(f.principal(f.a)))).isEmpty();assertThat(status(id)).isEqualTo("FAILED");}
 @Test void policyBoundsAndClassification(){var p=new PrintJobRetryPolicy(5,300,5,300,0);assertThat(p.delaySeconds(1)).isEqualTo(5);assertThat(p.delaySeconds(30)).isEqualTo(300);assertThat(p.transientError("PRINTER_UNAVAILABLE")).isTrue();assertThat(p.transientError("INVALID_SNAPSHOT")).isFalse();assertThatThrownBy(()->PrintJobRetryPolicy.validateCode(false,"SECRET arbitrary text")).isInstanceOf(ReglaNegocioException.class);}
 @Test void expiredLegacySuccessCanBeReconciledWithoutReprinting(){UUID id=job();var j=tx.execute(t->service.siguiente(f.principal(f.a),false).orElseThrow());expire(id);assertThat(tx.<Optional<TrabajoImpresionDto>>execute(t->service.siguiente(f.principal(f.a)))).isEmpty();tx.executeWithoutResult(t->service.completar(f.principal(f.a),id,j.claimId(),true));tx.executeWithoutResult(t->service.completar(f.principal(f.a),id,j.claimId(),true));assertThat(status(id)).isEqualTo("PRINTED");}
 @Test void expiredLastAttemptSetsTerminalTimestamp(){UUID id=job();claim();expire(id);claim();expire(id);assertThat(tx.<Optional<TrabajoImpresionDto>>execute(t->service.siguiente(f.principal(f.a)))).isEmpty();assertThat(status(id)).isEqualTo("FAILED");assertThat(f.jdbc.queryForObject("select completed_at is not null from billing_print_jobs where id=?",Boolean.class,id)).isTrue();}
 @Test void expiredReservationIsRecoveredEvenWhenAllAssignmentsWereRemoved(){UUID id=job();var j=claim();start(j);expire(id);f.jdbc.update("delete from print_agent_cash_registers where agent_id=?",f.a);assertThat(tx.<Optional<TrabajoImpresionDto>>execute(t->service.siguiente(f.principal(f.a)))).isEmpty();assertThat(status(id)).isEqualTo("FAILED");assertThat(f.jdbc.queryForObject("select last_error_code from billing_print_jobs where id=?",String.class,id)).isEqualTo("OUTPUT_UNCERTAIN");}
}
