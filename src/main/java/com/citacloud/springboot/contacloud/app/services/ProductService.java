package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.ProductoMapper;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.dao.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service
public class ProductService {
    private static final Set<Integer> PAGE_SIZES=Set.of(10,25,50,100);
    private final ProductRepository products;private final ProductoCategoriaRepository categories;private final UnidadMedidaRepository units;
    private final MonedaRepository currencies;private final ImpuestoRepository taxes;private final ProductCodeService codes;
    private final ProductoMapper mapper;private final AuditoriaService audit;
    public ProductService(ProductRepository products,ProductoCategoriaRepository categories,UnidadMedidaRepository units,
            MonedaRepository currencies,ImpuestoRepository taxes,ProductCodeService codes,ProductoMapper mapper,AuditoriaService audit){
        this.products=products;this.categories=categories;this.units=units;this.currencies=currencies;this.taxes=taxes;
        this.codes=codes;this.mapper=mapper;this.audit=audit;}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('productos.ver')")
    public Page<ProductoDto> searchProducts(String search,UUID category,TipoProducto type,Boolean active,int page,int size,String sort,boolean ascending){
        if(page<0||!PAGE_SIZES.contains(size))throw new ReglaNegocioException("Paginación inválida.");var p=TenantContext.principalActual();
        return products.buscar(p.tenantId(),p.empresaId(),clean(search),category,type,active,PageRequest.of(page,size,order(sort,ascending))).map(mapper::toDto);}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('productos.ver')")
    public ProductoDto getProduct(UUID id){return mapper.toDto(safe(id));}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAnyAuthority('productos.ver','productos.crear','productos.editar')")
    public ProductoCatalogosDto catalogs(){var p=TenantContext.principalActual();UUID company=p.empresaId();List<UnidadMedida> availableUnits=units.findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(p.tenantId(),company);
        var categoryOptions=categories.findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(p.tenantId(),company).stream().map(c->new ProductoCatalogosDto.CategoriaOpcion(c.getId(),c.getNombre())).toList();
        var unitOptions=availableUnits.stream().map(u->new ProductoCatalogosDto.UnidadOpcion(u.getId(),u.getNombre(),u.getAbreviatura())).toList();
        var money=currencies.findAllByEmpresaIdAndActivoTrueOrderByCodigoIso(company).stream().map(m->new ProductoCatalogosDto.MonedaOpcion(m.getId(),m.getCodigoIso(),m.getNombre())).toList();
        var taxOptions=taxes.findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(p.tenantId(),company).stream().map(t->new ProductoCatalogosDto.ImpuestoOpcion(t.getId(),t.getNombre(),t.getPorcentaje())).toList();
        UUID base=currencies.findByEmpresaIdAndMonedaBaseTrue(company).filter(Moneda::isActivo).map(Moneda::getId).orElse(null);
        return new ProductoCatalogosDto(categoryOptions,unitOptions,money,taxOptions,base);}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('productos.editar')")
    public ProductoCatalogosDto.UnidadOpcion unitOption(UUID id){var p=TenantContext.principalActual();UnidadMedida unit=units.findByIdAndTenantIdAndEmpresaId(id,p.tenantId(),p.empresaId()).orElseThrow(()->new RecursoNoEncontradoException("Unidad de medida no encontrada."));return new ProductoCatalogosDto.UnidadOpcion(unit.getId(),unit.getNombre(),unit.getAbreviatura());}

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('productos.crear')")
    public ProductoDto createProduct(ProductoInput input){var p=TenantContext.principalActual();Validated v=validate(input,p.tenantId(),p.empresaId(),null);
        Producto product=mapper.toEntity(v.input(),p.tenantId(),p.empresaId(),codes.next(p.tenantId(),p.empresaId(),v.input().tipo()),
            v.name(),v.barcode(),v.description(),v.track(),v.negative(),v.minimum(),p.usuarioId());
        try{product=products.saveAndFlush(product);}catch(DataIntegrityViolationException ex){throw duplicate(ex);}
        audit.registrar("PRODUCT_CREATED","Producto",product.getId(),detail(product));return mapper.toDto(product);}

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('productos.editar')")
    public ProductoDto updateProduct(UUID id,ProductoInput input){Producto product=safe(id);expected(product,input==null?null:input.version());
        Validated v=validate(input,product.getTenantId(),product.getEmpresaId(),product.getId());
        if(v.input().activo()!=product.isActivo())throw new ReglaNegocioException("Utiliza la acción correspondiente para cambiar el estado del producto.");
        product.actualizar(v.name(),v.input().tipo(),v.input().categoriaId(),v.input().unidadMedidaId(),v.barcode(),v.description(),
            v.input().costoCompra(),v.input().precioVenta(),v.input().monedaId(),v.input().impuestoCompraId(),v.input().impuestoVentaId(),
            v.track(),v.negative(),v.minimum(),TenantContext.principalActual().usuarioId());
        try{product=products.saveAndFlush(product);}catch(OptimisticLockingFailureException ex){throw concurrency(ex);}catch(DataIntegrityViolationException ex){throw duplicate(ex);}
        audit.registrar("PRODUCT_UPDATED","Producto",product.getId(),detail(product));return mapper.toDto(product);}

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('productos.desactivar')")
    public void deactivateProduct(UUID id){changeState(safe(id),false,"PRODUCT_DEACTIVATED");}
    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('productos.reactivar')")
    public void reactivateProduct(UUID id){changeState(safe(id),true,"PRODUCT_REACTIVATED");}

    private void changeState(Producto p,boolean active,String event){if(p.isActivo()==active)throw new ReglaNegocioException(active?"El producto ya está activo.":"El producto ya está inactivo.");
        p.cambiarEstado(active,TenantContext.principalActual().usuarioId());try{products.saveAndFlush(p);}catch(OptimisticLockingFailureException ex){throw concurrency(ex);}audit.registrar(event,"Producto",p.getId(),detail(p));}
    private Validated validate(ProductoInput input,UUID tenant,UUID company,UUID currentId){if(input==null)throw new ReglaNegocioException("Los datos del producto son obligatorios.");
        String name=required(input.nombre(),180,"El nombre es obligatorio.","El nombre excede 180 caracteres.");
        if(input.tipo()==null)throw new ReglaNegocioException("Selecciona el tipo.");
        if(input.unidadMedidaId()==null)throw new ReglaNegocioException("Selecciona una unidad de medida.");
        units.findByIdAndTenantIdAndEmpresaId(input.unidadMedidaId(),tenant,company).filter(UnidadMedida::isActivo).orElseThrow(()->new ReglaNegocioException("La unidad de medida seleccionada no está disponible."));
        if(input.categoriaId()!=null)categories.findByIdAndTenantIdAndEmpresaId(input.categoriaId(),tenant,company).filter(ProductoCategoria::isActivo).orElseThrow(()->new ReglaNegocioException("La categoría seleccionada no está disponible."));
        nonNegative(input.costoCompra(),"El costo de compra no puede ser negativo.");nonNegative(input.precioVenta(),"El precio de venta no puede ser negativo.");nonNegative(input.stockMinimo(),"El stock mínimo no puede ser negativo.");
        if((input.costoCompra()!=null||input.precioVenta()!=null)&&input.monedaId()==null)throw new ReglaNegocioException("Selecciona la moneda de los importes.");
        if(input.monedaId()!=null)currencies.findByIdAndEmpresaId(input.monedaId(),company).filter(Moneda::isActivo).orElseThrow(()->new ReglaNegocioException("La moneda seleccionada no está disponible."));
        validateTax(input.impuestoCompraId(),tenant,company);validateTax(input.impuestoVentaId(),tenant,company);
        String barcode=optional(input.codigoBarras(),100,"El código de barras excede 100 caracteres.");
        boolean duplicate=barcode!=null&&(currentId==null?products.existsByTenantIdAndEmpresaIdAndCodigoBarras(tenant,company,barcode):products.existsByTenantIdAndEmpresaIdAndCodigoBarrasAndIdNot(tenant,company,barcode,currentId));
        if(duplicate)throw new ReglaNegocioException("El código de barras ya está registrado.");String description=optional(input.descripcion(),1000,"La descripción excede 1000 caracteres.");
        boolean track=input.tipo()==TipoProducto.PRODUCT&&input.controlaExistencia();boolean negative=track&&input.permiteExistenciaNegativa();BigDecimal minimum=track?input.stockMinimo():null;
        return new Validated(input,name,barcode,description,track,negative,minimum);}
    private void validateTax(UUID id,UUID tenant,UUID company){if(id!=null)taxes.findByIdAndTenantIdAndEmpresaId(id,tenant,company).filter(Impuesto::isActivo).orElseThrow(()->new ReglaNegocioException("El impuesto seleccionado no está disponible."));}
    private Producto safe(UUID id){if(id==null)throw new RecursoNoEncontradoException("Producto no encontrado.");var p=TenantContext.principalActual();return products.findByIdAndTenantIdAndEmpresaId(id,p.tenantId(),p.empresaId()).orElseThrow(()->new RecursoNoEncontradoException("Producto no encontrado."));}
    private static void expected(Producto p,Long version){if(version==null||p.getVersion()!=version)throw concurrency(null);}
    private static void nonNegative(BigDecimal value,String message){if(value!=null&&value.signum()<0)throw new ReglaNegocioException(message);}
    private static Sort order(String field,boolean ascending){String property=switch(field==null?"nombre":field){case"codigo"->"codigo";case"tipo"->"tipo";case"precio"->"precioVenta";case"estado"->"activo";default->"nombre";};return Sort.by(ascending?Sort.Direction.ASC:Sort.Direction.DESC,property).and(Sort.by("nombre"));}
    private static String clean(String value){return value==null?"":value.trim();}
    private static String required(String value,int max,String empty,String tooLong){String v=clean(value);if(v.isEmpty())throw new ReglaNegocioException(empty);if(v.length()>max)throw new ReglaNegocioException(tooLong);return v;}
    private static String optional(String value,int max,String tooLong){String v=clean(value);if(v.isEmpty())return null;if(v.length()>max)throw new ReglaNegocioException(tooLong);return v;}
    private static ReglaNegocioException duplicate(Exception ex){return new ReglaNegocioException("El código interno o el código de barras ya está registrado.",ex);}
    private static ReglaNegocioException concurrency(Exception ex){return new ReglaNegocioException("El producto fue modificado por otro usuario. Actualiza la información antes de continuar.",ex);}
    private static String detail(Producto p){return "{\"codigo\":\""+escape(p.getCodigo())+"\",\"tipo\":\""+p.getTipo()+"\",\"activo\":"+p.isActivo()+"}";}
    private static String escape(String value){return value==null?"":value.replace("\\","\\\\").replace("\"","\\\"");}
    private record Validated(ProductoInput input,String name,String barcode,String description,boolean track,boolean negative,BigDecimal minimum){}
}
