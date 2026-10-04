package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.ImpuestoMapper;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.ImpuestoRepository;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ImpuestoFiscalServiceTest {
    private final ImpuestoRepository repository=mock(ImpuestoRepository.class);private final AuditoriaService audit=mock(AuditoriaService.class);
    private final ImpuestoFiscalService service=new ImpuestoFiscalService(repository,audit,new ImpuestoMapper());
    private final UUID tenant=UUID.randomUUID(),company=UUID.randomUUID(),user=UUID.randomUUID();

    @BeforeEach void setup(){var principal=new TenantPrincipal(user,tenant,company,null,"DEMO","Administrador","admin","",true,true,Set.of(),Set.of("impuestos.ver","impuestos.crear","impuestos.editar","impuestos.desactivar","impuestos.reactivar"));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));when(repository.saveAndFlush(any())).thenAnswer(inv->{Impuesto tax=inv.getArgument(0);if(tax.getId()==null)ReflectionTestUtils.setField(tax,"id",UUID.randomUUID());return tax;});}
    @AfterEach void clear(){SecurityContextHolder.clearContext();}

    @Test void createsNormalizedPercentageTaxAndAudits(){ImpuestoFiscalDto result=service.crear(input("  ITBIS   18%  ","18.000000",null,true,null));assertThat(result.nombre()).isEqualTo("ITBIS 18%");assertThat(result.tasa()).isEqualByComparingTo("18");assertThat(result.tipo()).isEqualTo("PERCENTAGE");verify(repository).existsByTenantIdAndEmpresaIdAndNombreNormalizado(tenant,company,"itbis 18%");verify(audit).registrar(eq("TAX_CREATED"),eq("Impuesto"),any(),contains("18"));}
    @Test void acceptsZeroAndOneHundredAndRejectsOutOfRange(){assertThatCode(()->ImpuestoFiscalService.validar(input("Exento","0",null,true,null))).doesNotThrowAnyException();assertThatCode(()->ImpuestoFiscalService.validar(input("Total","100",null,true,null))).doesNotThrowAnyException();assertThatThrownBy(()->ImpuestoFiscalService.validar(input("Negativo","-0.01",null,true,null))).hasMessage("La tasa debe estar entre 0.00% y 100.00%.");assertThatThrownBy(()->ImpuestoFiscalService.validar(input("Exceso","100.01",null,true,null))).hasMessage("La tasa debe estar entre 0.00% y 100.00%.");}
    @Test void rejectsRequiredInvalidAndDuplicateData(){assertThatThrownBy(()->ImpuestoFiscalService.validar(input("   ","18",null,true,null))).hasMessage("El nombre es obligatorio.");assertThatThrownBy(()->ImpuestoFiscalService.validar(new ImpuestoFiscalInput("ITBIS",null,"PERCENTAGE",null,true,null))).hasMessage("La tasa es obligatoria.");assertThatThrownBy(()->ImpuestoFiscalService.validar(new ImpuestoFiscalInput("ITBIS",BigDecimal.TEN,"FIXED",null,true,null))).hasMessage("El tipo de impuesto no es válido.");when(repository.existsByTenantIdAndEmpresaIdAndNombreNormalizado(tenant,company,"itbis")).thenReturn(true);assertThatThrownBy(()->service.crear(input("ITBIS","18",null,true,null))).hasMessage("Ya existe un impuesto con este nombre.");}
    @Test void updatesRateWithOptimisticLockAndAudit(){Impuesto tax=tax("ITBIS 18%","18",true);when(repository.findByIdAndTenantIdAndEmpresaId(tax.getId(),tenant,company)).thenReturn(Optional.of(tax));ImpuestoFiscalDto result=service.actualizar(tax.getId(),input("ITBIS 16%","16","Nueva tasa",true,0L));assertThat(result.tasa()).isEqualByComparingTo("16");verify(repository).existsByTenantIdAndEmpresaIdAndNombreNormalizadoAndIdNot(tenant,company,"itbis 16%",tax.getId());verify(audit).registrar(eq("TAX_UPDATED"),eq("Impuesto"),eq(tax.getId()),argThat(detail->detail.contains("18")&&detail.contains("16")));}
    @Test void rejectsStaleUpdate(){Impuesto tax=tax("ITBIS","18",true);ReflectionTestUtils.setField(tax,"version",2L);when(repository.findByIdAndTenantIdAndEmpresaId(tax.getId(),tenant,company)).thenReturn(Optional.of(tax));assertThatThrownBy(()->service.actualizar(tax.getId(),input("ITBIS","18",null,true,1L))).hasMessage("El impuesto fue modificado por otro usuario. Actualiza la información e inténtalo nuevamente.");}
    @Test void deactivatesAndReactivatesWithoutDeleting(){Impuesto tax=tax("Exento","0",true);when(repository.findByIdAndTenantIdAndEmpresaId(tax.getId(),tenant,company)).thenReturn(Optional.of(tax));service.desactivar(tax.getId(),0);assertThat(tax.isActivo()).isFalse();service.reactivar(tax.getId(),0);assertThat(tax.isActivo()).isTrue();verify(repository,never()).delete(any());verify(audit).registrar(eq("TAX_DEACTIVATED"),eq("Impuesto"),eq(tax.getId()),anyString());verify(audit).registrar(eq("TAX_REACTIVATED"),eq("Impuesto"),eq(tax.getId()),anyString());}
    @Test void searchesOnlyInsideCurrentContextWithBackendPagination(){when(repository.buscar(eq(tenant),eq(company),eq("itbis"),eq(true),any(Pageable.class))).thenReturn(Page.empty());service.buscar(" ITBIS ",true,0,10);verify(repository).buscar(eq(tenant),eq(company),eq("itbis"),eq(true),argThat(p->p.getPageNumber()==0&&p.getPageSize()==10&&p.getSort().getOrderFor("nombre")!=null));}
    @Test void doesNotResolveTaxFromAnotherCompany(){UUID id=UUID.randomUUID();when(repository.findByIdAndTenantIdAndEmpresaId(id,tenant,company)).thenReturn(Optional.empty());assertThatThrownBy(()->service.obtener(id)).isInstanceOf(RecursoNoEncontradoException.class).hasMessage("Impuesto no encontrado.");}

    private ImpuestoFiscalInput input(String name,String rate,String description,boolean active,Long version){return new ImpuestoFiscalInput(name,new BigDecimal(rate),"PERCENTAGE",description,active,version);}
    private Impuesto tax(String name,String rate,boolean active){Impuesto tax=new Impuesto(tenant,company,"IMP-TEST",name,ImpuestoFiscalService.normalizar(name),new BigDecimal(rate),TipoImpuesto.PERCENTAGE,null,active,user);ReflectionTestUtils.setField(tax,"id",UUID.randomUUID());return tax;}
}
