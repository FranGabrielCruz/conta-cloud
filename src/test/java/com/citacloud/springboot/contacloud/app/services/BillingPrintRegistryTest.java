package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.security.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BillingPrintRegistryTest {
 final UUID tenant=UUID.randomUUID(),company=UUID.randomUUID(),branch=UUID.randomUUID(),cash=UUID.randomUUID(),id=UUID.randomUUID(),attempt=UUID.randomUUID();
 final BillingPrintRouter router=mock(BillingPrintRouter.class);
 @SuppressWarnings("unchecked") final Supplier<ArchivoPdfDto> screen=mock(Supplier.class),printer=mock(Supplier.class);
 @BeforeEach void setup(){auth(Set.of("PRINT_TEST"));when(screen.get()).thenReturn(new ArchivoPdfDto("screen.pdf",new byte[]{1}));}
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 void auth(Set<String> permissions){var p=new TenantPrincipal(UUID.randomUUID(),tenant,company,null,"TEST","Prueba","test","",true,true,Set.of(),permissions);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));}
 List<BillingPrintDocumentRegistry.Registration> registrations(){return Arrays.stream(TipoDocumentoImpresion.values()).map(t->new BillingPrintDocumentRegistry.Registration(t,"PRINT_TEST",ignored->new BillingPrintDocumentRegistry.Document(branch,cash,screen,printer))).toList();}
 @ParameterizedTest @EnumSource(TipoDocumentoImpresion.class) void noPuedeAgregarseTipoSinAdaptador(TipoDocumentoImpresion missing){assertThatThrownBy(()->new BillingPrintDocumentRegistry(registrations().stream().filter(r->r.type()!=missing).toList())).isInstanceOf(IllegalStateException.class).hasMessageContaining(missing.name());}
 @Test void rechazaAdaptadoresDuplicados(){var list=new ArrayList<>(registrations());list.add(list.getFirst());assertThatThrownBy(()->new BillingPrintDocumentRegistry(list)).isInstanceOf(IllegalStateException.class);}
 @ParameterizedTest @EnumSource(TipoDocumentoImpresion.class) void pantallaSoloRenderizaPantallaDespuesDeResolverPolitica(TipoDocumentoImpresion type){
  when(router.solicitar(any(),eq(attempt))).thenReturn(new SolicitudImpresionResultadoDto(null,true));
  var service=new BillingPrintApplicationService(new BillingPrintDocumentRegistry(registrations()),router);var result=service.imprimir(type,id,attempt);
  assertThat(result.outputMode()).isEqualTo(BillingPrintOutputMode.SCREEN);verify(screen).get();verifyNoInteractions(printer);
 }
 @ParameterizedTest @EnumSource(TipoDocumentoImpresion.class) void impresoraNoRenderizaNiDevuelvePantalla(TipoDocumentoImpresion type){
  when(router.solicitar(any(),eq(attempt))).thenReturn(new SolicitudImpresionResultadoDto(UUID.randomUUID(),false));
  var result=new BillingPrintApplicationService(new BillingPrintDocumentRegistry(registrations()),router).imprimir(type,id,attempt);
  assertThat(result.outputMode()).isEqualTo(BillingPrintOutputMode.PRINT_AGENT);assertThat(result.screenDocument()).isNull();verifyNoInteractions(screen,printer);
 }
 @ParameterizedTest @EnumSource(TipoDocumentoImpresion.class) void permisoSeValidaAntesDeLeerDocumento(TipoDocumentoImpresion type){auth(Set.of());var registry=new BillingPrintDocumentRegistry(registrations());assertThatThrownBy(()->registry.load(type,id)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);verifyNoInteractions(screen,printer,router);}
 @Test void documentoSinCajaNoInventaDestino(){var list=registrations().stream().map(r->new BillingPrintDocumentRegistry.Registration(r.type(),r.permission(),ignored->new BillingPrintDocumentRegistry.Document(branch,null,screen,printer))).toList();assertThatThrownBy(()->new BillingPrintDocumentRegistry(list).load(TipoDocumentoImpresion.CASH_COUNT,id)).isInstanceOf(ReglaNegocioException.class);verifyNoInteractions(router);}
}
