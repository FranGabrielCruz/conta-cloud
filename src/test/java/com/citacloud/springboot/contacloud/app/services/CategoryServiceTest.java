package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.CategoriaMapper;
import com.citacloud.springboot.contacloud.app.models.ProductoCategoria;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CategoryServiceTest {
    private final ProductoCategoriaRepository categories=mock(ProductoCategoriaRepository.class);
    private final ProductRepository products=mock(ProductRepository.class);private final AuditoriaService audit=mock(AuditoriaService.class);
    private final CategoryService service=new CategoryService(categories,products,new CategoriaMapper(),audit);
    private final UUID tenant=UUID.randomUUID(),company=UUID.randomUUID(),user=UUID.randomUUID();

    @BeforeEach void setup(){var principal=new TenantPrincipal(user,tenant,company,null,"DEMO","Administrador","admin","",true,true,Set.of(),Set.of("categorias.ver","categorias.crear","categorias.editar","categorias.desactivar","categorias.reactivar"));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));when(categories.saveAndFlush(any())).thenAnswer(inv->{ProductoCategoria category=inv.getArgument(0);if(category.getId()==null)ReflectionTestUtils.setField(category,"id",UUID.randomUUID());return category;});}
    @AfterEach void clear(){SecurityContextHolder.clearContext();}

    @Test void createsTrimmedCategoryWithOptionalDescriptionAndAudit(){CategoriaDto result=service.createCategory(new CategoriaInput("  Equipos   de cómputo  ","  Computadoras y accesorios.  ",true,null));assertThat(result.nombre()).isEqualTo("Equipos de cómputo");assertThat(result.descripcion()).isEqualTo("Computadoras y accesorios.");assertThat(result.activo()).isTrue();assertThat(result.productosServicios()).isZero();verify(audit).registrar(eq("CATEGORY_CREATED"),eq("ProductoCategoria"),any(),anyString());}
    @Test void normalizesEmptyDescriptionToNull(){assertThat(CategoryService.validate(new CategoriaInput("Alimentos","   ",true,null)).description()).isNull();}
    @Test void requiresNameAndValidatesLengths(){assertThatThrownBy(()->CategoryService.validate(new CategoriaInput("   ",null,true,null))).hasMessage("El nombre es obligatorio.");assertThatThrownBy(()->CategoryService.validate(new CategoriaInput("x".repeat(121),null,true,null))).hasMessage("El nombre excede 120 caracteres.");assertThatThrownBy(()->CategoryService.validate(new CategoriaInput("Nombre","x".repeat(501),true,null))).hasMessage("La descripción excede 500 caracteres.");}
    @Test void normalizesCaseAndExternalOrRepeatedSpaces(){assertThat(CategoryService.normalize("  EQUIPOS   de CÓMPUTO ")).isEqualTo("equipos de cómputo");}
    @Test void rejectsDuplicateNormalizedName(){when(categories.existsByTenantIdAndEmpresaIdAndNombreNormalizado(tenant,company,"alimentos")).thenReturn(true);assertThatThrownBy(()->service.createCategory(new CategoriaInput(" alimentos ",null,true,null))).hasMessage("Ya existe una categoría con este nombre.");verify(categories,never()).saveAndFlush(any());}
    @Test void updatesWithoutConsideringItselfDuplicate(){ProductoCategoria category=category(true);when(categories.findByIdAndTenantIdAndEmpresaId(category.getId(),tenant,company)).thenReturn(Optional.of(category));when(products.countByTenantIdAndEmpresaIdAndCategoriaId(tenant,company,category.getId())).thenReturn(3L);CategoriaDto result=service.updateCategory(category.getId(),new CategoriaInput(" Alimentos ","Nueva descripción",true,0L));assertThat(result.descripcion()).isEqualTo("Nueva descripción");assertThat(result.productosServicios()).isEqualTo(3);verify(categories).existsByTenantIdAndEmpresaIdAndNombreNormalizadoAndIdNot(tenant,company,"alimentos",category.getId());verify(audit).registrar(eq("CATEGORY_UPDATED"),eq("ProductoCategoria"),eq(category.getId()),anyString());}
    @Test void deactivatesAndReactivatesWithoutDeletingRelatedProducts(){ProductoCategoria category=category(true);when(categories.findByIdAndTenantIdAndEmpresaId(category.getId(),tenant,company)).thenReturn(Optional.of(category));service.deactivateCategory(category.getId(),0);assertThat(category.isActivo()).isFalse();service.reactivateCategory(category.getId(),0);assertThat(category.isActivo()).isTrue();verify(categories,never()).delete(any());verify(audit).registrar(eq("CATEGORY_DEACTIVATED"),eq("ProductoCategoria"),eq(category.getId()),anyString());verify(audit).registrar(eq("CATEGORY_REACTIVATED"),eq("ProductoCategoria"),eq(category.getId()),anyString());}
    @Test void rejectsStaleVersion(){ProductoCategoria category=category(true);ReflectionTestUtils.setField(category,"version",2L);when(categories.findByIdAndTenantIdAndEmpresaId(category.getId(),tenant,company)).thenReturn(Optional.of(category));assertThatThrownBy(()->service.updateCategory(category.getId(),new CategoriaInput("Alimentos",null,true,1L))).hasMessage("La categoría fue modificada por otro usuario. Actualiza la información e inténtalo nuevamente.");}
    @Test void isolatesLookupByTenantAndCompany(){UUID id=UUID.randomUUID();when(categories.findByIdAndTenantIdAndEmpresaId(id,tenant,company)).thenReturn(Optional.empty());assertThatThrownBy(()->service.getCategory(id)).isInstanceOf(RecursoNoEncontradoException.class).hasMessage("Categoría no encontrada.");}
    @Test void searchesWithBackendPaginationAndCalculatedCounts(){ProductoCategoria category=category(true);Page<ProductoCategoria> page=new PageImpl<>(List.of(category),PageRequest.of(0,10),1);when(categories.buscar(eq(tenant),eq(company),eq("equipo"),eq(true),any(Pageable.class))).thenReturn(page);when(products.contarPorCategorias(eq(tenant),eq(company),anyCollection())).thenReturn(List.<Object[]>of(new Object[]{category.getId(),4L}));Page<CategoriaDto> result=service.searchCategories(" equipo ",true,0,10);assertThat(result.getContent()).singleElement().extracting(CategoriaDto::productosServicios).isEqualTo(4L);verify(categories).buscar(eq(tenant),eq(company),eq("equipo"),eq(true),argThat(p->p.getPageNumber()==0&&p.getPageSize()==10&&p.getSort().getOrderFor("nombre")!=null));}

    private ProductoCategoria category(boolean active){ProductoCategoria category=new ProductoCategoria(tenant,company,"Alimentos","alimentos",null,active,user);ReflectionTestUtils.setField(category,"id",UUID.randomUUID());return category;}
}
