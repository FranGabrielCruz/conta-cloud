package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import java.util.*;
import java.nio.file.*;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class PrintJobSnapshotTest {
 @TempDir Path directory;
 @Test void migrationPreservesOldJobsAndRequiresVersionedBoundedSnapshot()throws Exception{
  String url="jdbc:h2:mem:print_snapshot;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
  try(var connection=DriverManager.getConnection(url,"sa","")){
   var jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));jdbc.execute("create table billing_print_jobs(id uuid primary key,document_pdf binary varying)");
   UUID old=UUID.randomUUID();jdbc.update("insert into billing_print_jobs values(?,?)",old,new byte[]{1});
   Files.copy(Path.of("src/main/resources/db/migration/V110__snapshot_logico_trabajos_impresion.sql"),directory.resolve("V110__snapshot_logico_trabajos_impresion.sql"));
   Flyway.configure().dataSource(url,"sa","").locations("filesystem:"+directory).baselineOnMigrate(true).baselineVersion("109").load().migrate();
   assertThat(jdbc.queryForObject("select document_pdf from billing_print_jobs where id=?",byte[].class,old)).containsExactly((byte)1);
   assertThat(jdbc.queryForObject("select snapshot_version from billing_print_jobs where id=?",Integer.class,old)).isNull();
   assertThatThrownBy(()->jdbc.update("update billing_print_jobs set snapshot_json='{}' where id=?",old)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
   jdbc.update("update billing_print_jobs set snapshot_json=?,snapshot_version=1 where id=?","{}",old);
   assertThatThrownBy(()->jdbc.update("update billing_print_jobs set snapshot_version=2 where id=?",old)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }
 }
 @ParameterizedTest @EnumSource(TipoDocumentoImpresion.class)
 void routerCapturesSnapshotOnlyForNewPhysicalJobsAndNeverForScreenOrReplay(TipoDocumentoImpresion type){
  var matrix=new BillingPrintMatrixTest();matrix.setup();try{
   when(matrix.renderer.get()).thenReturn(new ArchivoPdfDto("ticket.pdf",new byte[]{1}));
   @SuppressWarnings("unchecked") java.util.function.Supplier<Object> snapshot=mock(java.util.function.Supplier.class);
   var d=new BillingPrintRouter.Documento(type,matrix.id,matrix.branch,matrix.cash,matrix.renderer,snapshot);
   when(matrix.agent.isPrintOnScreen()).thenReturn(true);matrix.router.solicitar(d,matrix.attempt);verifyNoInteractions(snapshot);
   when(matrix.agent.isPrintOnScreen()).thenReturn(false);when(snapshot.get()).thenReturn(PrintSnapshotFixtures.invoice());
   UUID job=UUID.randomUUID();var output=new SolicitudImpresionResultadoDto(job,false);
   when(matrix.jdbc.query(contains("select id,print_on_screen"),org.mockito.ArgumentMatchers.<RowMapper<SolicitudImpresionResultadoDto>>any(),eq(matrix.tenant),eq(matrix.company),eq(matrix.attempt),eq(matrix.id))).thenReturn(List.of(),List.of(),List.of(output),List.of(output));
   when(matrix.jdbc.update(startsWith("insert into billing_print_jobs"),any(Object[].class))).thenReturn(1);
   matrix.router.solicitar(d,matrix.attempt);matrix.router.solicitar(d,matrix.attempt);verify(snapshot).get();
   var json=org.mockito.ArgumentCaptor.forClass(String.class);
   verify(matrix.jdbc).update(contains("set snapshot_json=?"),json.capture(),anyString(),any(UUID.class),eq(matrix.tenant),eq(matrix.company),eq(matrix.agentId));
   var decoded=tools.jackson.databind.json.JsonMapper.builder().build().readValue(json.getValue(),com.citacloud.springboot.contacloud.app.reports.pos.PosInvoiceDocument.class);
   assertThat(decoded).isEqualTo(PrintSnapshotFixtures.invoice());assertThat(json.getValue()).contains("2026-11-07T17:00:00");
  }finally{matrix.clear();}
 }
 @Test void claimDeliversStoredSnapshotOnlyWithinAuthorizedAgentTenantCompanyAndClaim(){
  var test=new BillingPrintServiceTest();var a=test.activo();when(a.getCajas()).thenReturn(Set.of(mock(Caja.class)));UUID id=UUID.randomUUID();
  when(test.jdbc.query(contains("for update of j skip locked"),org.mockito.ArgumentMatchers.<RowMapper<UUID>>any(),eq(test.p.tenantId()),eq(test.p.companyId()),eq(test.p.branchId()),eq(test.p.agentId()))).thenReturn(List.of(id));
  when(test.jdbc.update(contains("set status='PROCESSING'"),any(UUID.class),eq(id),eq(test.p.tenantId()),eq(test.p.companyId()),eq(test.p.agentId()))).thenReturn(1);
  when(test.jdbc.queryForObject(contains("select snapshot_json"),eq(String.class),eq(id),eq(test.p.tenantId()),eq(test.p.companyId()),eq(test.p.agentId()),any(UUID.class))).thenReturn("{stored}");
  when(test.jdbc.queryForObject(contains("select snapshot_version"),eq(Integer.class),eq(id),eq(test.p.tenantId()),eq(test.p.companyId()),eq(test.p.agentId()),any(UUID.class))).thenReturn(1);
  var result=test.service.siguiente(test.p).orElseThrow();assertThat(result.snapshot()).isEqualTo("{stored}");assertThat(result.snapshotVersion()).isEqualTo(1);
 }
}
