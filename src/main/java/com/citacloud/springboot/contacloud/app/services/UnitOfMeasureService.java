package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.UnidadMedidaMapper;
import com.citacloud.springboot.contacloud.app.models.UnidadMedida;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.dao.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.text.Normalizer;
import java.util.*;

@Service
public class UnitOfMeasureService {
    private static final Set<Integer> PAGE_SIZES=Set.of(10,25,50,100);
    private final UnidadMedidaRepository repository;private final ProductRepository products;
    private final UnidadMedidaMapper mapper;private final AuditoriaService audit;
    public UnitOfMeasureService(UnidadMedidaRepository repository,ProductRepository products,UnidadMedidaMapper mapper,AuditoriaService audit){this.repository=repository;this.products=products;this.mapper=mapper;this.audit=audit;}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('unidades_medida.ver')")
    public Page<UnidadMedidaDto> searchUnits(String search,Boolean active,int page,int size){validatePage(page,size);var p=TenantContext.principalActual();return repository.buscar(p.tenantId(),EmpresaContext.requerirEmpresaId(),normalize(search),active,PageRequest.of(page,size,Sort.by("nombre").ascending())).map(mapper::toDto);}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('unidades_medida.ver')")
    public UnidadMedidaDto getUnit(UUID id){return mapper.toDto(safe(id));}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAnyAuthority('unidades_medida.ver','productos.crear','productos.editar')")
    public List<UnidadMedidaDto> getActiveUnits(){var p=TenantContext.principalActual();return repository.findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(p.tenantId(),EmpresaContext.requerirEmpresaId()).stream().map(mapper::toDto).toList();}

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('unidades_medida.crear')")
    public UnidadMedidaDto createUnit(UnidadMedidaInput input){Validated v=validate(input);var p=TenantContext.principalActual();UUID company=EmpresaContext.requerirEmpresaId();duplicates(p.tenantId(),company,null,v);UnidadMedida entity=new UnidadMedida(p.tenantId(),company,v.name(),v.normalizedName(),v.abbreviation(),v.normalizedAbbreviation(),v.description(),input.activo(),p.usuarioId());try{entity=repository.saveAndFlush(entity);}catch(DataIntegrityViolationException ex){throw duplicate(ex);}audit.registrar("UNIT_OF_MEASURE_CREATED","UnidadMedida",entity.getId(),detail(entity));return mapper.toDto(entity);}

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('unidades_medida.editar')")
    public UnidadMedidaDto updateUnit(UUID id,UnidadMedidaInput input){UnidadMedida entity=safe(id);if(input==null||input.version()==null||entity.getVersion()!=input.version())throw concurrency(null);Validated v=validate(input);if(input.activo()!=entity.isActivo())throw new ReglaNegocioException("Utiliza la acción correspondiente para cambiar el estado de la unidad de medida.");duplicates(entity.getTenantId(),entity.getEmpresaId(),entity.getId(),v);entity.actualizar(v.name(),v.normalizedName(),v.abbreviation(),v.normalizedAbbreviation(),v.description(),TenantContext.principalActual().usuarioId());try{entity=repository.saveAndFlush(entity);}catch(OptimisticLockingFailureException ex){throw concurrency(ex);}catch(DataIntegrityViolationException ex){throw duplicate(ex);}audit.registrar("UNIT_OF_MEASURE_UPDATED","UnidadMedida",entity.getId(),detail(entity));return mapper.toDto(entity);}

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('unidades_medida.desactivar')")
    public void deactivateUnit(UUID id,long version){UnidadMedida entity=safe(id);expected(entity,version);if(!entity.isActivo())throw new ReglaNegocioException("La unidad de medida ya está inactiva.");if(products.existsByTenantIdAndEmpresaIdAndUnidadMedidaIdAndActivoTrue(entity.getTenantId(),entity.getEmpresaId(),entity.getId()))throw new ReglaNegocioException("No puedes desactivar esta unidad de medida porque está asignada a uno o más productos activos.");changeState(entity,false,"UNIT_OF_MEASURE_DEACTIVATED");}

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('unidades_medida.reactivar')")
    public void reactivateUnit(UUID id,long version){UnidadMedida entity=safe(id);expected(entity,version);if(entity.isActivo())throw new ReglaNegocioException("La unidad de medida ya está activa.");Validated v=new Validated(entity.getNombre(),entity.getNombreNormalizado(),entity.getAbreviatura(),entity.getAbreviaturaNormalizada(),entity.getDescripcion());duplicates(entity.getTenantId(),entity.getEmpresaId(),entity.getId(),v);changeState(entity,true,"UNIT_OF_MEASURE_REACTIVATED");}

    private void changeState(UnidadMedida entity,boolean active,String event){entity.cambiarEstado(active,TenantContext.principalActual().usuarioId());try{repository.saveAndFlush(entity);}catch(OptimisticLockingFailureException ex){throw concurrency(ex);}audit.registrar(event,"UnidadMedida",entity.getId(),detail(entity));}
    private UnidadMedida safe(UUID id){if(id==null)throw new RecursoNoEncontradoException("Unidad de medida no encontrada.");return repository.findByIdAndTenantIdAndEmpresaId(id,TenantContext.requerirTenantId(),EmpresaContext.requerirEmpresaId()).orElseThrow(()->new RecursoNoEncontradoException("Unidad de medida no encontrada."));}
    private void duplicates(UUID tenant,UUID company,UUID current,Validated v){boolean name=current==null?repository.existsByTenantIdAndEmpresaIdAndNombreNormalizado(tenant,company,v.normalizedName()):repository.existsByTenantIdAndEmpresaIdAndNombreNormalizadoAndIdNot(tenant,company,v.normalizedName(),current);if(name)throw new ReglaNegocioException("Ya existe una unidad de medida con este nombre.");boolean abbreviation=current==null?repository.existsByTenantIdAndEmpresaIdAndAbreviaturaNormalizada(tenant,company,v.normalizedAbbreviation()):repository.existsByTenantIdAndEmpresaIdAndAbreviaturaNormalizadaAndIdNot(tenant,company,v.normalizedAbbreviation(),current);if(abbreviation)throw new ReglaNegocioException("Ya existe una unidad de medida con esta abreviatura.");}
    static Validated validate(UnidadMedidaInput input){if(input==null)throw new ReglaNegocioException("Los datos de la unidad de medida son obligatorios.");String name=required(input.nombre(),80,"El nombre es obligatorio.","El nombre excede 80 caracteres.");String abbreviation=required(input.abreviatura(),20,"La abreviatura es obligatoria.","La abreviatura excede 20 caracteres.");String description=optional(input.descripcion(),500,"La descripción excede 500 caracteres.");return new Validated(name,normalize(name),abbreviation,normalize(abbreviation),description);}
    static String normalize(String value){String clean=value==null?"":value.trim().replaceAll("\\s+"," ");return Normalizer.normalize(clean,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);}
    private static String required(String value,int max,String empty,String tooLong){String clean=value==null?"":value.trim();if(clean.isEmpty())throw new ReglaNegocioException(empty);if(clean.length()>max)throw new ReglaNegocioException(tooLong);return clean;}
    private static String optional(String value,int max,String tooLong){String clean=value==null?"":value.trim();if(clean.isEmpty())return null;if(clean.length()>max)throw new ReglaNegocioException(tooLong);return clean;}
    private static void validatePage(int page,int size){if(page<0||!PAGE_SIZES.contains(size))throw new ReglaNegocioException("Paginación inválida.");}
    private static void expected(UnidadMedida entity,long version){if(entity.getVersion()!=version)throw concurrency(null);}
    private static ReglaNegocioException concurrency(Exception ex){return new ReglaNegocioException("La unidad de medida fue modificada por otro usuario. Actualiza la información antes de continuar.",ex);}
    private static ReglaNegocioException duplicate(DataIntegrityViolationException ex){String message=ex.getMostSpecificCause()==null?"":ex.getMostSpecificCause().getMessage().toLowerCase(Locale.ROOT);return new ReglaNegocioException(message.contains("abbreviation")?"Ya existe una unidad de medida con esta abreviatura.":"Ya existe una unidad de medida con este nombre.",ex);}
    private static String detail(UnidadMedida entity){return "{\"nombre\":\""+escape(entity.getNombre())+"\",\"abreviatura\":\""+escape(entity.getAbreviatura())+"\",\"activo\":"+entity.isActivo()+"}";}
    private static String escape(String value){return value==null?"":value.replace("\\","\\\\").replace("\"","\\\"");}
    record Validated(String name,String normalizedName,String abbreviation,String normalizedAbbreviation,String description){}
}
