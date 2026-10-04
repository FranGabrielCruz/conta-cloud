package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.ProductoMapper;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
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

class ProductServiceTest {
    private final ProductRepository products=mock(ProductRepository.class);private final ProductoCategoriaRepository categories=mock(ProductoCategoriaRepository.class);
    private final UnidadMedidaRepository units=mock(UnidadMedidaRepository.class);private final MonedaRepository currencies=mock(MonedaRepository.class);
    private final ImpuestoRepository taxes=mock(ImpuestoRepository.class);private final ProductCodeService codes=mock(ProductCodeService.class);
    private final AuditoriaService audit=mock(AuditoriaService.class);private final ProductService service=new ProductService(products,categories,units,currencies,taxes,codes,new ProductoMapper(),audit);
    private final UUID tenant=UUID.randomUUID(),company=UUID.randomUUID(),user=UUID.randomUUID(),unitId=UUID.randomUUID(),currencyId=UUID.randomUUID(),taxId=UUID.randomUUID();
    @BeforeEach void setup(){var principal=new TenantPrincipal(user,tenant,company,null,"DEMO","Administrador","admin","",true,true,Set.of(),Set.of("productos.ver","productos.crear","productos.editar","productos.desactivar","productos.reactivar"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        UnidadMedida unit=mock(UnidadMedida.class);when(unit.isActivo()).thenReturn(true);when(units.findByIdAndTenantIdAndEmpresaId(unitId,tenant,company)).thenReturn(Optional.of(unit));
        Moneda currency=mock(Moneda.class);when(currency.isActivo()).thenReturn(true);when(currencies.findByIdAndEmpresaId(currencyId,company)).thenReturn(Optional.of(currency));
        Impuesto tax=mock(Impuesto.class);when(tax.isActivo()).thenReturn(true);when(taxes.findByIdAndTenantIdAndEmpresaId(taxId,tenant,company)).thenReturn(Optional.of(tax));
        when(codes.next(any(),any(),eq(TipoProducto.PRODUCT))).thenReturn("PRD-000001");when(codes.next(any(),any(),eq(TipoProducto.SERVICE))).thenReturn("SRV-000001");
        when(products.saveAndFlush(any())).thenAnswer(inv->{Producto p=inv.getArgument(0);if(p.getId()==null)ReflectionTestUtils.setField(p,"id",UUID.randomUUID());return p;});}
    @AfterEach void clear(){SecurityContextHolder.clearContext();}

    @Test void createsProductWithAutomaticCodeAndAudit(){ProductoDto result=service.createProduct(input(TipoProducto.PRODUCT,true,false,new BigDecimal("5")));
        assertThat(result.codigo()).isEqualTo("PRD-000001");assertThat(result.controlaExistencia()).isTrue();assertThat(result.stockMinimo()).isEqualByComparingTo("5");
        verify(audit).registrar(eq("PRODUCT_CREATED"),eq("Producto"),any(),anyString());}
    @Test void createsServiceAndClearsInventoryValues(){ProductoDto result=service.createProduct(input(TipoProducto.SERVICE,true,true,new BigDecimal("5")));
        assertThat(result.codigo()).isEqualTo("SRV-000001");assertThat(result.controlaExistencia()).isFalse();assertThat(result.permiteExistenciaNegativa()).isFalse();assertThat(result.stockMinimo()).isNull();}
    @Test void productMayNotTrackInventory(){ProductoDto result=service.createProduct(input(TipoProducto.PRODUCT,false,true,new BigDecimal("5")));
        assertThat(result.controlaExistencia()).isFalse();assertThat(result.permiteExistenciaNegativa()).isFalse();assertThat(result.stockMinimo()).isNull();}
    @Test void validatesRequiredFieldsAndAmounts(){ProductoInput base=input(TipoProducto.PRODUCT,true,false,BigDecimal.ZERO);
        assertThatThrownBy(()->service.createProduct(copy(base,"   ",base.tipo(),base.unidadMedidaId(),base.costoCompra(),base.precioVenta(),base.stockMinimo()))).hasMessage("El nombre es obligatorio.");
        assertThatThrownBy(()->service.createProduct(copy(base,base.nombre(),null,base.unidadMedidaId(),base.costoCompra(),base.precioVenta(),base.stockMinimo()))).hasMessage("Selecciona el tipo.");
        assertThatThrownBy(()->service.createProduct(copy(base,base.nombre(),base.tipo(),null,base.costoCompra(),base.precioVenta(),base.stockMinimo()))).hasMessage("Selecciona una unidad de medida.");
        assertThatThrownBy(()->service.createProduct(copy(base,base.nombre(),base.tipo(),base.unidadMedidaId(),new BigDecimal("-1"),base.precioVenta(),base.stockMinimo()))).hasMessage("El costo de compra no puede ser negativo.");
        assertThatThrownBy(()->service.createProduct(copy(base,base.nombre(),base.tipo(),base.unidadMedidaId(),base.costoCompra(),new BigDecimal("-1"),base.stockMinimo()))).hasMessage("El precio de venta no puede ser negativo.");
        assertThatThrownBy(()->service.createProduct(copy(base,base.nombre(),base.tipo(),base.unidadMedidaId(),base.costoCompra(),base.precioVenta(),new BigDecimal("-1")))).hasMessage("El stock mínimo no puede ser negativo.");}
    @Test void acceptsZeroMonetaryValues(){ProductoDto result=service.createProduct(input(TipoProducto.PRODUCT,true,false,BigDecimal.ZERO));assertThat(result.costoCompra()).isEqualByComparingTo(BigDecimal.ZERO);assertThat(result.precioVenta()).isEqualByComparingTo(BigDecimal.ZERO);}
    @Test void rejectsDuplicateBarcode(){when(products.existsByTenantIdAndEmpresaIdAndCodigoBarras(tenant,company,"0012345")).thenReturn(true);ProductoInput base=input(TipoProducto.PRODUCT,true,false,BigDecimal.ZERO);
        ProductoInput duplicate=new ProductoInput(base.nombre(),base.tipo(),null,base.unidadMedidaId()," 0012345 ",null,base.costoCompra(),base.precioVenta(),base.monedaId(),null,null,true,false,BigDecimal.ZERO,true,null);
        assertThatThrownBy(()->service.createProduct(duplicate)).hasMessage("El código de barras ya está registrado.");verify(products,never()).saveAndFlush(any());}
    @Test void rejectsRelationsFromAnotherCompany(){when(units.findByIdAndTenantIdAndEmpresaId(unitId,tenant,company)).thenReturn(Optional.empty());assertThatThrownBy(()->service.createProduct(input(TipoProducto.PRODUCT,true,false,BigDecimal.ZERO))).hasMessage("La unidad de medida seleccionada no está disponible.");}
    @Test void rejectsInactiveCategoryForNewAssignments(){UUID categoryId=UUID.randomUUID();ProductoCategoria category=mock(ProductoCategoria.class);when(category.isActivo()).thenReturn(false);when(categories.findByIdAndTenantIdAndEmpresaId(categoryId,tenant,company)).thenReturn(Optional.of(category));ProductoInput base=input(TipoProducto.PRODUCT,true,false,BigDecimal.ZERO);ProductoInput categorized=new ProductoInput(base.nombre(),base.tipo(),categoryId,base.unidadMedidaId(),base.codigoBarras(),base.descripcion(),base.costoCompra(),base.precioVenta(),base.monedaId(),base.impuestoCompraId(),base.impuestoVentaId(),base.controlaExistencia(),base.permiteExistenciaNegativa(),base.stockMinimo(),base.activo(),null);assertThatThrownBy(()->service.createProduct(categorized)).hasMessage("La categoría seleccionada no está disponible.");}
    @Test void preservesCurrentInactiveCategoryWhenEditing(){UUID categoryId=UUID.randomUUID();ProductoCategoria category=mock(ProductoCategoria.class);when(category.isActivo()).thenReturn(false);when(categories.findByIdAndTenantIdAndEmpresaId(categoryId,tenant,company)).thenReturn(Optional.of(category));Producto product=new Producto(tenant,company,"PRD-000001","Laptop",TipoProducto.PRODUCT,categoryId,unitId,null,null,BigDecimal.ZERO,BigDecimal.ZERO,currencyId,null,null,true,false,BigDecimal.ZERO,true,user);ReflectionTestUtils.setField(product,"id",UUID.randomUUID());when(products.findByIdAndTenantIdAndEmpresaId(product.getId(),tenant,company)).thenReturn(Optional.of(product));ProductoInput update=new ProductoInput("Laptop",TipoProducto.PRODUCT,categoryId,unitId,null,null,BigDecimal.ZERO,BigDecimal.ZERO,currencyId,null,null,true,false,BigDecimal.ZERO,true,0L);assertThatCode(()->service.updateProduct(product.getId(),update)).doesNotThrowAnyException();}
    @Test void preservesCurrentInactiveTaxesWhenEditing(){Impuesto inactive=mock(Impuesto.class);when(inactive.isActivo()).thenReturn(false);when(taxes.findByIdAndTenantIdAndEmpresaId(taxId,tenant,company)).thenReturn(Optional.of(inactive));Producto product=new Producto(tenant,company,"PRD-000001","Laptop",TipoProducto.PRODUCT,null,unitId,null,null,BigDecimal.ZERO,BigDecimal.ZERO,currencyId,taxId,taxId,true,false,BigDecimal.ZERO,true,user);ReflectionTestUtils.setField(product,"id",UUID.randomUUID());when(products.findByIdAndTenantIdAndEmpresaId(product.getId(),tenant,company)).thenReturn(Optional.of(product));ProductoInput update=new ProductoInput("Laptop",TipoProducto.PRODUCT,null,unitId,null,null,BigDecimal.ZERO,BigDecimal.ZERO,currencyId,taxId,taxId,true,false,BigDecimal.ZERO,true,0L);assertThatCode(()->service.updateProduct(product.getId(),update)).doesNotThrowAnyException();}
    @Test void searchesWithBackendPagination(){when(products.buscar(eq(tenant),eq(company),eq("lap"),isNull(),eq(TipoProducto.PRODUCT),eq(true),any(Pageable.class))).thenReturn(Page.empty());
        service.searchProducts(" lap ",null,TipoProducto.PRODUCT,true,0,10,"nombre",true);verify(products).buscar(eq(tenant),eq(company),eq("lap"),isNull(),eq(TipoProducto.PRODUCT),eq(true),argThat(p->p.getPageNumber()==0&&p.getPageSize()==10));}
    @Test void doesNotExposeAnotherCompanyProduct(){UUID id=UUID.randomUUID();when(products.findByIdAndTenantIdAndEmpresaId(id,tenant,company)).thenReturn(Optional.empty());assertThatThrownBy(()->service.getProduct(id)).isInstanceOf(RecursoNoEncontradoException.class).hasMessage("Producto no encontrado.");}
    @Test void deactivatesAndReactivatesWithoutDeleting(){Producto product=entity(true);when(products.findByIdAndTenantIdAndEmpresaId(product.getId(),tenant,company)).thenReturn(Optional.of(product));service.deactivateProduct(product.getId());assertThat(product.isActivo()).isFalse();service.reactivateProduct(product.getId());assertThat(product.isActivo()).isTrue();verify(products,never()).delete(any());verify(audit).registrar(eq("PRODUCT_DEACTIVATED"),eq("Producto"),eq(product.getId()),anyString());}
    @Test void rejectsStaleUpdate(){Producto product=entity(true);ReflectionTestUtils.setField(product,"version",3L);when(products.findByIdAndTenantIdAndEmpresaId(product.getId(),tenant,company)).thenReturn(Optional.of(product));ProductoInput update=input(TipoProducto.PRODUCT,true,false,BigDecimal.ZERO);ProductoInput stale=new ProductoInput(update.nombre(),update.tipo(),null,update.unidadMedidaId(),null,null,update.costoCompra(),update.precioVenta(),update.monedaId(),null,null,true,false,BigDecimal.ZERO,true,2L);assertThatThrownBy(()->service.updateProduct(product.getId(),stale)).hasMessage("El producto fue modificado por otro usuario. Actualiza la información antes de continuar.");}

    private ProductoInput input(TipoProducto type,boolean track,boolean negative,BigDecimal minimum){return new ProductoInput(" Laptop Dell ",type,null,unitId,null,"Equipo",BigDecimal.ZERO,BigDecimal.ZERO,currencyId,taxId,taxId,track,negative,minimum,true,null);}
    private ProductoInput copy(ProductoInput i,String name,TipoProducto type,UUID unit,BigDecimal cost,BigDecimal price,BigDecimal minimum){return new ProductoInput(name,type,null,unit,null,null,cost,price,currencyId,null,null,true,false,minimum,true,null);}
    private Producto entity(boolean active){Producto p=new Producto(tenant,company,"PRD-000001","Laptop",TipoProducto.PRODUCT,null,unitId,null,null,BigDecimal.ZERO,BigDecimal.ZERO,currencyId,null,null,true,false,BigDecimal.ZERO,active,user);ReflectionTestUtils.setField(p,"id",UUID.randomUUID());return p;}
}
