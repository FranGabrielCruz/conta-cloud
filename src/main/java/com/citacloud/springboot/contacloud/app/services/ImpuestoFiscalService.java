package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.ImpuestoMapper;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.ImpuestoRepository;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;

@Service
public class ImpuestoFiscalService {
    private static final Set<Integer> PAGE_SIZES=Set.of(10,25,50,100);
    private final ImpuestoRepository repo;private final AuditoriaService auditoria;private final ImpuestoMapper mapper;
    public ImpuestoFiscalService(ImpuestoRepository repo,AuditoriaService auditoria,ImpuestoMapper mapper){this.repo=repo;this.auditoria=auditoria;this.mapper=mapper;}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('IMPUESTOS') and hasAuthority('impuestos.ver')")
    public Page<ImpuestoFiscalDto> buscar(String q,Boolean activo,int page,int size){validatePage(page,size);return repo.buscar(TenantContext.requerirTenantId(),EmpresaContext.requerirEmpresaId(),normalizar(q),activo,PageRequest.of(page,size,Sort.by("nombre").ascending())).map(mapper::toDto);}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('IMPUESTOS') and hasAuthority('impuestos.ver')")
    public ImpuestoFiscalDto obtener(UUID id){return mapper.toDto(seguro(id));}

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('IMPUESTOS') and hasAuthority('impuestos.crear')")
    public ImpuestoFiscalDto crear(ImpuestoFiscalInput input){Validado v=validar(input);var p=TenantContext.principalActual();UUID empresa=EmpresaContext.requerirEmpresaId();duplicado(p.tenantId(),empresa,null,v.nombreNormalizado());UUID random=UUID.randomUUID();Impuesto impuesto=new Impuesto(p.tenantId(),empresa,"IMP-"+random.toString().substring(0,8).toUpperCase(Locale.ROOT),v.nombre(),v.nombreNormalizado(),v.tasa(),v.tipo(),v.descripcion(),input.activo(),p.usuarioId());try{impuesto=repo.saveAndFlush(impuesto);}catch(DataIntegrityViolationException ex){throw duplicado(ex);}auditoria.registrar("TAX_CREATED","Impuesto",impuesto.getId(),detalle(null,impuesto));return mapper.toDto(impuesto);}

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('IMPUESTOS') and hasAuthority('impuestos.editar')")
    public ImpuestoFiscalDto actualizar(UUID id,ImpuestoFiscalInput input){Impuesto impuesto=seguro(id);esperado(impuesto,input==null?null:input.version());Validado v=validar(input);if(input.activo()!=impuesto.isActivo())throw new ReglaNegocioException("Utiliza la acción correspondiente para cambiar el estado del impuesto.");duplicado(impuesto.getTenantId(),impuesto.getEmpresaId(),impuesto.getId(),v.nombreNormalizado());String anterior=estado(impuesto);impuesto.actualizar(v.nombre(),v.nombreNormalizado(),v.tasa(),v.tipo(),v.descripcion(),TenantContext.principalActual().usuarioId());try{impuesto=repo.saveAndFlush(impuesto);}catch(ObjectOptimisticLockingFailureException ex){throw concurrencia(ex);}catch(DataIntegrityViolationException ex){throw duplicado(ex);}auditoria.registrar("TAX_UPDATED","Impuesto",id,detalle(anterior,impuesto));return mapper.toDto(impuesto);}

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('IMPUESTOS') and hasAuthority('impuestos.desactivar')")
    public void desactivar(UUID id,long version){Impuesto impuesto=seguro(id);esperado(impuesto,version);if(!impuesto.isActivo())throw new ReglaNegocioException("El impuesto ya está inactivo.");cambiarEstado(impuesto,false,"TAX_DEACTIVATED");}

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('IMPUESTOS') and hasAuthority('impuestos.reactivar')")
    public void reactivar(UUID id,long version){Impuesto impuesto=seguro(id);esperado(impuesto,version);if(impuesto.isActivo())throw new ReglaNegocioException("El impuesto ya está activo.");duplicado(impuesto.getTenantId(),impuesto.getEmpresaId(),impuesto.getId(),impuesto.getNombreNormalizado());cambiarEstado(impuesto,true,"TAX_REACTIVATED");}

    private void cambiarEstado(Impuesto impuesto,boolean activo,String evento){String anterior=estado(impuesto);impuesto.cambiarEstado(activo,TenantContext.principalActual().usuarioId());try{repo.saveAndFlush(impuesto);}catch(ObjectOptimisticLockingFailureException ex){throw concurrencia(ex);}auditoria.registrar(evento,"Impuesto",impuesto.getId(),detalle(anterior,impuesto));}
    private Impuesto seguro(UUID id){if(id==null)throw new RecursoNoEncontradoException("Impuesto no encontrado.");return repo.findByIdAndTenantIdAndEmpresaId(id,TenantContext.requerirTenantId(),EmpresaContext.requerirEmpresaId()).orElseThrow(()->new RecursoNoEncontradoException("Impuesto no encontrado."));}
    private void duplicado(UUID tenant,UUID empresa,UUID actual,String nombre){boolean existe=actual==null?repo.existsByTenantIdAndEmpresaIdAndNombreNormalizado(tenant,empresa,nombre):repo.existsByTenantIdAndEmpresaIdAndNombreNormalizadoAndIdNot(tenant,empresa,nombre,actual);if(existe)throw new ReglaNegocioException("Ya existe un impuesto con este nombre.");}
    static Validado validar(ImpuestoFiscalInput input){if(input==null)throw new ReglaNegocioException("Los datos del impuesto son obligatorios.");String nombre=requerido(input.nombre(),120,"El nombre es obligatorio.","El nombre excede 120 caracteres.");if(input.tasa()==null)throw new ReglaNegocioException("La tasa es obligatoria.");if(input.tasa().compareTo(BigDecimal.ZERO)<0||input.tasa().compareTo(new BigDecimal("100"))>0)throw new ReglaNegocioException("La tasa debe estar entre 0.00% y 100.00%.");TipoImpuesto tipo;try{tipo=TipoImpuesto.valueOf(input.tipo()==null?"":input.tipo().trim().toUpperCase(Locale.ROOT));}catch(IllegalArgumentException ex){throw new ReglaNegocioException("El tipo de impuesto no es válido.");}return new Validado(nombre,normalizar(nombre),input.tasa(),tipo,opcional(input.descripcion(),500,"La descripción excede 500 caracteres."));}
    static String normalizar(String valor){String limpio=valor==null?"":valor.trim().replaceAll("\\s+"," ");return Normalizer.normalize(limpio,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);}
    private static String requerido(String valor,int max,String vacio,String largo){String limpio=valor==null?"":valor.trim().replaceAll("\\s+"," ");if(limpio.isEmpty())throw new ReglaNegocioException(vacio);if(limpio.length()>max)throw new ReglaNegocioException(largo);return limpio;}
    private static String opcional(String valor,int max,String largo){String limpio=valor==null?"":valor.trim();if(limpio.isEmpty())return null;if(limpio.length()>max)throw new ReglaNegocioException(largo);return limpio;}
    private static void validatePage(int page,int size){if(page<0||!PAGE_SIZES.contains(size))throw new ReglaNegocioException("Paginación inválida.");}
    private static void esperado(Impuesto impuesto,Long version){if(version==null||impuesto.getVersion()!=version)throw concurrencia(null);}
    private static ReglaNegocioException concurrencia(Exception ex){return new ReglaNegocioException("El impuesto fue modificado por otro usuario. Actualiza la información e inténtalo nuevamente.",ex);}
    private static ReglaNegocioException duplicado(DataIntegrityViolationException ex){return new ReglaNegocioException("Ya existe un impuesto con este nombre.",ex);}
    private static String estado(Impuesto i){return "{\"nombre\":\""+escape(i.getNombre())+"\",\"tasa\":"+i.getPorcentaje()+",\"tipo\":\""+i.getTipo()+"\",\"activo\":"+i.isActivo()+"}";}
    private static String detalle(String anterior,Impuesto actual){return "{\"anterior\":"+(anterior==null?"null":anterior)+",\"nuevo\":"+estado(actual)+"}";}
    private static String escape(String v){return v==null?"":v.replace("\\","\\\\").replace("\"","\\\"");}
    record Validado(String nombre,String nombreNormalizado,BigDecimal tasa,TipoImpuesto tipo,String descripcion){}
}
