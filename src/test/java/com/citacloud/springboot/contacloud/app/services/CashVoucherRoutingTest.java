package com.citacloud.springboot.contacloud.app.services;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
class CashVoucherRoutingTest {
 UUID tenant=UUID.randomUUID(),company=UUID.randomUUID(),branch=UUID.randomUUID(),cash=UUID.randomUUID(),agentId=UUID.randomUUID(),id=UUID.randomUUID();
 JdbcTemplate jdbc=mock(JdbcTemplate.class);AgenteImpresionRepository agents=mock(AgenteImpresionRepository.class);PrintAgentRoutingService routing=mock(PrintAgentRoutingService.class);AuditoriaService audit=mock(AuditoriaService.class);
 CashVoucherDocumentService documents=mock(CashVoucherDocumentService.class);BillingPrintApplicationService service;
 @SuppressWarnings("unchecked") java.util.function.Supplier<ArchivoPdfDto> screen=mock(java.util.function.Supplier.class),printer=mock(java.util.function.Supplier.class);
 AgenteImpresion agent;
 @BeforeEach void setup(){auth(Set.of("vales_caja.imprimir"));var definitions=Arrays.stream(TipoDocumentoImpresion.values()).map(t->t==TipoDocumentoImpresion.CASH_VOUCHER?new BillingPrintDocumentsConfiguration().cashVoucherPrintDocument(documents):new BillingPrintDocumentRegistry.Registration(t,"OTHER",x->{throw new AssertionError();})).toList();service=new BillingPrintApplicationService(new BillingPrintDocumentRegistry(definitions),new BillingPrintRouter(jdbc,routing,agents,audit));when(documents.cargar(id)).thenReturn(new BillingPrintDocumentRegistry.Document(branch,cash,screen,printer));when(printer.get()).thenReturn(new ArchivoPdfDto("vale.pdf",new byte[]{1}));when(screen.get()).thenReturn(new ArchivoPdfDto("vale.pdf",new byte[]{1}));agent=mock(AgenteImpresion.class);when(agent.getSucursalId()).thenReturn(branch);when(agent.getEstado()).thenReturn(EstadoAgenteImpresion.ACTIVE);var c=mock(Caja.class);when(c.getId()).thenReturn(cash);when(agent.getCajas()).thenReturn(Set.of(c));when(routing.resolver(cash)).thenReturn(agentId);when(agents.bloquear(agentId,tenant,company)).thenReturn(Optional.of(agent));}
 void auth(Set<String> permissions){var p=new TenantPrincipal(UUID.randomUUID(),tenant,company,null,"DEMO","Juan","juan","",true,true,Set.of(),permissions);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));}
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 @Test void screenCreatesZeroJobsAndNeverRendersPrinter(){when(agent.isPrintOnScreen()).thenReturn(true);var result=service.imprimir(TipoDocumentoImpresion.CASH_VOUCHER,id,UUID.randomUUID());assertThat(result.outputMode()).isEqualTo(BillingPrintOutputMode.SCREEN);assertThat(result.screenDocument()).isNotNull();verify(screen).get();verifyNoInteractions(printer);verify(jdbc,never()).update(contains("insert into billing_print_jobs"),any(Object[].class));}
 @Test void physicalUsesSemanticTypeOriginAndAgentAndRetryDoesNotDuplicate(){UUID attempt=UUID.randomUUID(),job=UUID.randomUUID();var output=new SolicitudImpresionResultadoDto(job,false);when(jdbc.query(contains("document_type='CASH_VOUCHER'"),org.mockito.ArgumentMatchers.<RowMapper<SolicitudImpresionResultadoDto>>any(),eq(tenant),eq(company),eq(attempt),eq(id))).thenReturn(List.of(),List.of(),List.of(output),List.of(output));when(jdbc.update(contains("insert into billing_print_jobs"),any(Object[].class))).thenReturn(1);assertThat(service.imprimir(TipoDocumentoImpresion.CASH_VOUCHER,id,attempt).outputMode()).isEqualTo(BillingPrintOutputMode.PRINT_AGENT);assertThat(service.imprimir(TipoDocumentoImpresion.CASH_VOUCHER,id,attempt).screenDocument()).isNull();verify(printer).get();verifyNoInteractions(screen);verify(jdbc).update(contains("insert into billing_print_jobs"),any(UUID.class),eq(tenant),eq(company),eq(branch),eq(cash),eq(agentId),eq(id),eq(attempt),any(byte[].class),eq(false),eq("CASH_VOUCHER"));verify(audit).registrar(eq("PrintJobCreated"),eq("PrintJob"),any(),contains("traceId"));}
 @Test void revokedHasNoPdfFallback(){when(agent.getEstado()).thenReturn(EstadoAgenteImpresion.REVOKED);assertThatThrownBy(()->service.imprimir(TipoDocumentoImpresion.CASH_VOUCHER,id,UUID.randomUUID())).isInstanceOf(ReglaNegocioException.class);verifyNoInteractions(screen,printer);}
 @Test void disconnectedOrMissingAgentHasNoFallback(){when(routing.resolver(cash)).thenThrow(new ReglaNegocioException("No disponible"));assertThatThrownBy(()->service.imprimir(TipoDocumentoImpresion.CASH_VOUCHER,id,UUID.randomUUID())).isInstanceOf(ReglaNegocioException.class);verifyNoInteractions(screen,printer);}
 @Test void removedBoxCannotRouteToAnotherAgent(){when(agent.getCajas()).thenReturn(Set.of());assertThatThrownBy(()->service.imprimir(TipoDocumentoImpresion.CASH_VOUCHER,id,UUID.randomUUID())).isInstanceOf(ReglaNegocioException.class);verifyNoInteractions(screen,printer);}
 @Test void unauthorizedFailsBeforeLoading(){auth(Set.of());assertThatThrownBy(()->service.imprimir(TipoDocumentoImpresion.CASH_VOUCHER,id,UUID.randomUUID())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);verify(documents,never()).cargar(any());verifyNoInteractions(jdbc,routing,screen,printer);}
 @Test void foreignVoucherNeverResolvesAgent(){when(documents.cargar(id)).thenThrow(new RecursoNoEncontradoException("No encontrado"));assertThatThrownBy(()->service.imprimir(TipoDocumentoImpresion.CASH_VOUCHER,id,UUID.randomUUID())).isInstanceOf(RecursoNoEncontradoException.class);verifyNoInteractions(jdbc,routing,screen,printer);}
}
