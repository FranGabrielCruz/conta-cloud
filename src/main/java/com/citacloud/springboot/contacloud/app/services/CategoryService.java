package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.CategoriaMapper;
import com.citacloud.springboot.contacloud.app.models.ProductoCategoria;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.dao.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CategoryService {
    private static final Set<Integer> PAGE_SIZES=Set.of(10,25,50,100);
    private final ProductoCategoriaRepository categories;private final ProductRepository products;
    private final CategoriaMapper mapper;private final AuditoriaService audit;
    public CategoryService(ProductoCategoriaRepository categories,ProductRepository products,CategoriaMapper mapper,AuditoriaService audit){this.categories=categories;this.products=products;this.mapper=mapper;this.audit=audit;}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('categorias.ver')")
    public Page<CategoriaDto> searchCategories(String search,Boolean active,int page,int size){validatePage(page,size);var p=TenantContext.principalActual();UUID company=EmpresaContext.requerirEmpresaId();Page<ProductoCategoria> result=categories.buscar(p.tenantId(),company,normalize(search),active,PageRequest.of(page,size,Sort.by("nombre").ascending()));Map<UUID,Long> counts=countByCategory(p.tenantId(),company,result.getContent());return result.map(c->mapper.toDto(c,counts.getOrDefault(c.getId(),0L)));}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('categorias.ver')")
    public CategoriaDto getCategory(UUID id){ProductoCategoria category=safe(id);return mapper.toDto(category,products.countByTenantIdAndEmpresaIdAndCategoriaId(category.getTenantId(),category.getEmpresaId(),category.getId()));}

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('categorias.crear')")
    public CategoriaDto createCategory(CategoriaInput input){Validated v=validate(input);var p=TenantContext.principalActual();UUID company=EmpresaContext.requerirEmpresaId();duplicate(p.tenantId(),company,null,v.normalizedName());ProductoCategoria category=new ProductoCategoria(p.tenantId(),company,v.name(),v.normalizedName(),v.description(),input.activo(),p.usuarioId());try{category=categories.saveAndFlush(category);}catch(DataIntegrityViolationException ex){throw duplicate(ex);}audit.registrar("CATEGORY_CREATED","ProductoCategoria",category.getId(),detail(category));return mapper.toDto(category,0);}

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('categorias.editar')")
    public CategoriaDto updateCategory(UUID id,CategoriaInput input){ProductoCategoria category=safe(id);if(input==null||input.version()==null||category.getVersion()!=input.version())throw concurrency(null);Validated v=validate(input);if(input.activo()!=category.isActivo())throw new ReglaNegocioException("Utiliza la acción correspondiente para cambiar el estado de la categoría.");duplicate(category.getTenantId(),category.getEmpresaId(),category.getId(),v.normalizedName());category.actualizar(v.name(),v.normalizedName(),v.description(),TenantContext.principalActual().usuarioId());try{category=categories.saveAndFlush(category);}catch(OptimisticLockingFailureException ex){throw concurrency(ex);}catch(DataIntegrityViolationException ex){throw duplicate(ex);}audit.registrar("CATEGORY_UPDATED","ProductoCategoria",category.getId(),detail(category));return mapper.toDto(category,products.countByTenantIdAndEmpresaIdAndCategoriaId(category.getTenantId(),category.getEmpresaId(),category.getId()));}

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('categorias.desactivar')")
    public void deactivateCategory(UUID id,long version){ProductoCategoria category=safe(id);expected(category,version);if(!category.isActivo())throw new ReglaNegocioException("La categoría ya está inactiva.");changeState(category,false,"CATEGORY_DEACTIVATED");}

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('categorias.reactivar')")
    public void reactivateCategory(UUID id,long version){ProductoCategoria category=safe(id);expected(category,version);if(category.isActivo())throw new ReglaNegocioException("La categoría ya está activa.");duplicate(category.getTenantId(),category.getEmpresaId(),category.getId(),category.getNombreNormalizado());changeState(category,true,"CATEGORY_REACTIVATED");}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAnyAuthority('productos.crear','productos.editar')")
    public List<ProductoCatalogosDto.CategoriaOpcion> activeOptions(){var p=TenantContext.principalActual();return categories.findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(p.tenantId(),EmpresaContext.requerirEmpresaId()).stream().map(CategoryService::option).toList();}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('productos.editar')")
    public ProductoCatalogosDto.CategoriaOpcion option(UUID id){return option(safe(id));}

    private void changeState(ProductoCategoria category,boolean active,String event){category.cambiarEstado(active,TenantContext.principalActual().usuarioId());try{categories.saveAndFlush(category);}catch(OptimisticLockingFailureException ex){throw concurrency(ex);}audit.registrar(event,"ProductoCategoria",category.getId(),detail(category));}
    private ProductoCategoria safe(UUID id){if(id==null)throw new RecursoNoEncontradoException("Categoría no encontrada.");return categories.findByIdAndTenantIdAndEmpresaId(id,TenantContext.requerirTenantId(),EmpresaContext.requerirEmpresaId()).orElseThrow(()->new RecursoNoEncontradoException("Categoría no encontrada."));}
    private void duplicate(UUID tenant,UUID company,UUID current,String name){boolean exists=current==null?categories.existsByTenantIdAndEmpresaIdAndNombreNormalizado(tenant,company,name):categories.existsByTenantIdAndEmpresaIdAndNombreNormalizadoAndIdNot(tenant,company,name,current);if(exists)throw new ReglaNegocioException("Ya existe una categoría con este nombre.");}
    static Validated validate(CategoriaInput input){if(input==null)throw new ReglaNegocioException("Los datos de la categoría son obligatorios.");String name=required(input.nombre(),120,"El nombre es obligatorio.","El nombre excede 120 caracteres.");return new Validated(name,normalize(name),optional(input.descripcion(),500,"La descripción excede 500 caracteres."));}
    static String normalize(String value){String clean=value==null?"":value.trim().replaceAll("\\s+"," ");return Normalizer.normalize(clean,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);}
    private Map<UUID,Long> countByCategory(UUID tenant,UUID company,List<ProductoCategoria> content){if(content.isEmpty())return Map.of();Set<UUID> ids=content.stream().map(ProductoCategoria::getId).collect(Collectors.toSet());return products.contarPorCategorias(tenant,company,ids).stream().collect(Collectors.toMap(row->(UUID)row[0],row->((Number)row[1]).longValue()));}
    private static ProductoCatalogosDto.CategoriaOpcion option(ProductoCategoria category){return new ProductoCatalogosDto.CategoriaOpcion(category.getId(),category.getNombre());}
    private static String required(String value,int max,String empty,String tooLong){String clean=value==null?"":value.trim().replaceAll("\\s+"," ");if(clean.isEmpty())throw new ReglaNegocioException(empty);if(clean.length()>max)throw new ReglaNegocioException(tooLong);return clean;}
    private static String optional(String value,int max,String tooLong){String clean=value==null?"":value.trim();if(clean.isEmpty())return null;if(clean.length()>max)throw new ReglaNegocioException(tooLong);return clean;}
    private static void validatePage(int page,int size){if(page<0||!PAGE_SIZES.contains(size))throw new ReglaNegocioException("Paginación inválida.");}
    private static void expected(ProductoCategoria category,long version){if(category.getVersion()!=version)throw concurrency(null);}
    private static ReglaNegocioException concurrency(Exception ex){return new ReglaNegocioException("La categoría fue modificada por otro usuario. Actualiza la información e inténtalo nuevamente.",ex);}
    private static ReglaNegocioException duplicate(DataIntegrityViolationException ex){return new ReglaNegocioException("Ya existe una categoría con este nombre.",ex);}
    private static String detail(ProductoCategoria category){return "{\"nombre\":\""+escape(category.getNombre())+"\",\"activo\":"+category.isActivo()+"}";}
    private static String escape(String value){return value==null?"":value.replace("\\","\\\\").replace("\"","\\\"");}
    record Validated(String name,String normalizedName,String description){}
}
