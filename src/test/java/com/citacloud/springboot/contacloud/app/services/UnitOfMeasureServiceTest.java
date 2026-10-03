package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.UnidadMedidaMapper;
import com.citacloud.springboot.contacloud.app.models.UnidadMedida;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UnitOfMeasureServiceTest {
    private final UnidadMedidaRepository units=mock(UnidadMedidaRepository.class);private final ProductRepository products=mock(ProductRepository.class);private final AuditoriaService audit=mock(AuditoriaService.class);private final UnitOfMeasureService service=new UnitOfMeasureService(units,products,new UnidadMedidaMapper(),audit);private final UUID tenant=UUID.randomUUID(),company=UUID.randomUUID(),user=UUID.randomUUID();
    @BeforeEach void setup(){var principal=new TenantPrincipal(user,tenant,company,null,"DEMO","Administrador","admin","",true,true,Set.of(),Set.of("unidades_medida.ver","unidades_medida.crear","unidades_medida.editar","unidades_medida.desactivar","unidades_medida.reactivar"));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));when(units.saveAndFlush(any())).thenAnswer(inv->{UnidadMedida unit=inv.getArgument(0);if(unit.getId()==null)ReflectionTestUtils.setField(unit,"id",UUID.randomUUID());return unit;});}
    @AfterEach void clear(){SecurityContextHolder.clearContext();}

    @Test void trimsAndNormalizesWithoutChangingVisualAbbreviation(){var input=new UnidadMedidaInput("  Metro cuadrado  "," m² "," Longitud ",true,null);UnidadMedidaDto result=service.createUnit(input);assertThat(result.nombre()).isEqualTo("Metro cuadrado");assertThat(result.abreviatura()).isEqualTo("m²");assertThat(result.descripcion()).isEqualTo("Longitud");verify(units).existsByTenantIdAndEmpresaIdAndNombreNormalizado(tenant,company,"metro cuadrado");}
    @Test void validatesRequiredFields(){assertThatThrownBy(()->UnitOfMeasureService.validate(new UnidadMedidaInput(" ","kg",null,true,null))).hasMessage("El nombre es obligatorio.");assertThatThrownBy(()->UnitOfMeasureService.validate(new UnidadMedidaInput("Kilogramo"," ",null,true,null))).hasMessage("La abreviatura es obligatoria.");}
    @Test void rejectsNormalizedDuplicateName(){when(units.existsByTenantIdAndEmpresaIdAndNombreNormalizado(tenant,company,"metro")).thenReturn(true);assertThatThrownBy(()->service.createUnit(new UnidadMedidaInput(" METRO ","m",null,true,null))).hasMessage("Ya existe una unidad de medida con este nombre.");verify(units,never()).saveAndFlush(any());}
    @Test void rejectsNormalizedDuplicateAbbreviation(){when(units.existsByTenantIdAndEmpresaIdAndAbreviaturaNormalizada(tenant,company,"kg")).thenReturn(true);assertThatThrownBy(()->service.createUnit(new UnidadMedidaInput("Kilogramo"," KG ",null,true,null))).hasMessage("Ya existe una unidad de medida con esta abreviatura.");}
    @Test void blocksDeactivationWhenActiveProductsUseTheUnit(){UnidadMedida unit=unit(true);when(units.findByIdAndTenantIdAndEmpresaId(unit.getId(),tenant,company)).thenReturn(Optional.of(unit));when(products.existsByTenantIdAndEmpresaIdAndUnidadMedidaIdAndActivoTrue(tenant,company,unit.getId())).thenReturn(true);assertThatThrownBy(()->service.deactivateUnit(unit.getId(),0)).hasMessage("No puedes desactivar esta unidad de medida porque está asignada a uno o más productos activos.");}
    @Test void deactivatesAndReactivatesWithoutDeleting(){UnidadMedida unit=unit(true);when(units.findByIdAndTenantIdAndEmpresaId(unit.getId(),tenant,company)).thenReturn(Optional.of(unit));service.deactivateUnit(unit.getId(),0);assertThat(unit.isActivo()).isFalse();service.reactivateUnit(unit.getId(),0);assertThat(unit.isActivo()).isTrue();verify(units,never()).delete(any());}
    @Test void rejectsStaleUpdate(){UnidadMedida unit=unit(true);ReflectionTestUtils.setField(unit,"version",2L);when(units.findByIdAndTenantIdAndEmpresaId(unit.getId(),tenant,company)).thenReturn(Optional.of(unit));assertThatThrownBy(()->service.updateUnit(unit.getId(),new UnidadMedidaInput("Metro","m",null,true,1L))).hasMessage("La unidad de medida fue modificada por otro usuario. Actualiza la información antes de continuar.");}

    private UnidadMedida unit(boolean active){UnidadMedida unit=new UnidadMedida(tenant,company,"Metro","metro","m","m",null,active,user);ReflectionTestUtils.setField(unit,"id",UUID.randomUUID());return unit;}
}
