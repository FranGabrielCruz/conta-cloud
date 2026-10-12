package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.AlmacenMapper;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.dao.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AlmacenService {
    private static final Set<Integer> PAGE_SIZES=Set.of(10,25,50,100);
    private final AlmacenRepository almacenes;private final AlmacenConsultaRepository consultas;
    private final SucursalRepository sucursales;private final UsuarioRepository usuarios;
    private final ProductoCategoriaRepository categorias;private final AlmacenNumeroService numeros;
    private final AlmacenMapper mapper;private final AuditoriaService auditoria;

    public AlmacenService(AlmacenRepository almacenes,AlmacenConsultaRepository consultas,SucursalRepository sucursales,
                          UsuarioRepository usuarios,ProductoCategoriaRepository categorias,AlmacenNumeroService numeros,
                          AlmacenMapper mapper,AuditoriaService auditoria){this.almacenes=almacenes;this.consultas=consultas;
        this.sucursales=sucursales;this.usuarios=usuarios;this.categorias=categorias;this.numeros=numeros;
        this.mapper=mapper;this.auditoria=auditoria;}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('almacenes.ver')")
    public Page<AlmacenDto> buscar(String buscar,UUID sucursal,TipoAlmacen tipo,Boolean activo,int page,int size){
        validarPagina(page,size);var p=TenantContext.principalActual();UUID empresa=EmpresaContext.requerirEmpresaId();
        if(sucursal!=null)sucursalSegura(sucursal,false);
        Page<Almacen> result=almacenes.buscar(p.tenantId(),empresa,normalizar(buscar),sucursal,tipo,activo,
            PageRequest.of(page,size,Sort.by("nombre").ascending()));
        Map<UUID,Long> counts=consultas.contarProductosConExistencia(p.tenantId(),empresa,result.getContent().stream().map(Almacen::getId).toList());
        return result.map(a->dto(a,counts.getOrDefault(a.getId(),0L)));
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('almacenes.ver')")
    public AlmacenDto obtener(UUID id){Almacen a=seguro(id);return dto(a,consultas.contarProductosConExistencia(a.getTenantId(),a.getEmpresaId(),a.getId()));}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('almacenes.ver')")
    public AlmacenCatalogosDto catalogos(){var p=TenantContext.principalActual();UUID empresa=EmpresaContext.requerirEmpresaId();
        var branches=sucursales.findAllByEmpresaIdAndActivoTrueOrderByNombre(empresa).stream()
            .map(s->new AlmacenCatalogosDto.Opcion(s.getId(),s.getNombre())).toList();
        var users=usuarios.findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombreAscApellidoAsc(p.tenantId(),empresa).stream()
            .map(u->new AlmacenCatalogosDto.Opcion(u.getId(),(u.getNombre()+" "+u.getApellido()).trim())).toList();
        return new AlmacenCatalogosDto(branches,users);
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('almacenes.ver')")
    public List<AlmacenCatalogosDto.Opcion> categorias(){var p=TenantContext.principalActual();return categorias
        .findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(p.tenantId(),EmpresaContext.requerirEmpresaId()).stream()
        .map(c->new AlmacenCatalogosDto.Opcion(c.getId(),c.getNombre())).toList();}

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('almacenes.crear')")
    public AlmacenDto crear(AlmacenInput input){Validado v=validar(input);var p=TenantContext.principalActual();UUID empresa=EmpresaContext.requerirEmpresaId();
        Sucursal branch=sucursalSegura(v.sucursalId(),true);Usuario responsible=responsableSeguro(v.responsableId());
        duplicado(p.tenantId(),empresa,null,v.sucursalId(),v.nombreNormalizado());
        Almacen a=new Almacen(p.tenantId(),empresa,numeros.siguiente(p.tenantId(),empresa),v.nombre(),v.nombreNormalizado(),
            v.sucursalId(),v.tipo(),v.responsableId(),v.direccion(),v.observacion(),p.usuarioId());
        a.asignarReferencias(branch,responsible);
        try{a=almacenes.saveAndFlush(a);}catch(DataIntegrityViolationException ex){throw duplicado(ex);}
        if(input.predeterminadoSucursal())predeterminar(branch,a.getId());
        auditoria.registrar("ALMACEN_CREAR","Almacen",a.getId(),detalle(a));
        return dto(a,0);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('almacenes.editar')")
    public AlmacenDto actualizar(UUID id,AlmacenInput input){Almacen a=seguro(id);esperada(a,input==null?null:input.version());Validado v=validar(input);
        Sucursal branch=sucursalSegura(v.sucursalId(),true);Usuario responsible=responsableSeguro(v.responsableId());
        if(!a.getSucursalId().equals(v.sucursalId())&&consultas.tieneHistorial(a.getTenantId(),a.getEmpresaId(),a.getId()))
            throw new ReglaNegocioException("No se puede cambiar la sucursal porque el almacén ya tiene movimientos o documentos relacionados.");
        duplicado(a.getTenantId(),a.getEmpresaId(),a.getId(),v.sucursalId(),v.nombreNormalizado());
        UUID oldBranch=a.getSucursalId();a.actualizar(v.nombre(),v.nombreNormalizado(),v.sucursalId(),v.tipo(),v.responsableId(),v.direccion(),v.observacion(),TenantContext.principalActual().usuarioId());
        a.asignarReferencias(branch,responsible);
        try{a=almacenes.saveAndFlush(a);}catch(OptimisticLockingFailureException ex){throw concurrencia(ex);}catch(DataIntegrityViolationException ex){throw duplicado(ex);}
        if(!oldBranch.equals(v.sucursalId()))limpiarPredeterminado(oldBranch,a.getId());
        if(input.predeterminadoSucursal())predeterminar(branch,a.getId());else limpiarPredeterminado(v.sucursalId(),a.getId());
        auditoria.registrar("ALMACEN_EDITAR","Almacen",a.getId(),detalle(a));return dto(a,consultas.contarProductosConExistencia(a.getTenantId(),a.getEmpresaId(),a.getId()));
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('almacenes.desactivar')")
    public void desactivar(UUID id,long version){Almacen a=seguro(id);esperada(a,version);if(!a.isActivo())throw new ReglaNegocioException("El almacén ya está inactivo.");
        if(esPredeterminado(a))throw new ReglaNegocioException("No se puede desactivar el almacén predeterminado de la sucursal. Selecciona otro almacén predeterminado primero.");
        if(consultas.tieneExistencias(a.getTenantId(),a.getEmpresaId(),a.getId()))throw new ReglaNegocioException("No se puede desactivar un almacén con existencias positivas o negativas.");
        if(consultas.tieneRecepcionesPendientes(a.getTenantId(),a.getEmpresaId(),a.getId()))throw new ReglaNegocioException("No se puede desactivar el almacén porque tiene recepciones pendientes.");
        cambiarEstado(a,false,"ALMACEN_DESACTIVAR");}

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('almacenes.activar')")
    public void activar(UUID id,long version){Almacen a=seguro(id);esperada(a,version);if(a.isActivo())throw new ReglaNegocioException("El almacén ya está activo.");
        sucursalSegura(a.getSucursalId(),true);duplicado(a.getTenantId(),a.getEmpresaId(),a.getId(),a.getSucursalId(),a.getNombreNormalizado());
        cambiarEstado(a,true,"ALMACEN_ACTIVAR");}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('almacenes.existencias')")
    public Page<ExistenciaAlmacenDto> existencias(UUID id,String buscar,UUID categoria,String estado,int page,int size){validarPagina(page,size);Almacen a=seguro(id);
        String value=Set.of("TODOS","CON_EXISTENCIA","SIN_EXISTENCIA").contains(estado)?estado:"TODOS";
        Page<ExistenciaAlmacenDto> result=consultas.existencias(a.getTenantId(),a.getEmpresaId(),a.getId(),buscar,categoria,value,PageRequest.of(page,size));
        if(a.getTipo()!=TipoAlmacen.GENERAL)return result.map(x->new ExistenciaAlmacenDto(x.productoId(),x.codigo(),x.producto(),x.categoria(),x.unidad(),x.existencia(),x.reservado(),null));
        return result;}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('almacenes.movimientos')")
    public Page<MovimientoAlmacenDto> movimientos(UUID id,String buscar,String tipo,LocalDate desde,LocalDate hasta,int page,int size){validarPagina(page,size);Almacen a=seguro(id);
        if(desde!=null&&hasta!=null&&hasta.isBefore(desde))throw new ReglaNegocioException("La fecha final no puede ser anterior a la fecha inicial.");
        return consultas.movimientos(a.getTenantId(),a.getEmpresaId(),a.getId(),buscar,tipo,desde,hasta,PageRequest.of(page,size));}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('almacenes.movimientos')")
    public List<String> tiposMovimiento(UUID id){Almacen a=seguro(id);return consultas.tiposMovimiento(a.getTenantId(),a.getEmpresaId(),a.getId());}

    private AlmacenDto dto(Almacen a,long count){return mapper.toDto(a,esPredeterminado(a),count);}
    private boolean esPredeterminado(Almacen a){return a.getSucursal()!=null&&a.getId().equals(a.getSucursal().getAlmacenPredeterminadoId());}
    private void predeterminar(Sucursal branch,UUID warehouse){branch.setAlmacenPredeterminadoId(warehouse);sucursales.save(branch);}
    private void limpiarPredeterminado(UUID branchId,UUID warehouse){sucursales.findByIdAndEmpresaId(branchId,EmpresaContext.requerirEmpresaId()).ifPresent(s->{if(warehouse.equals(s.getAlmacenPredeterminadoId())){s.setAlmacenPredeterminadoId(null);sucursales.save(s);}});}
    private void cambiarEstado(Almacen a,boolean active,String event){a.cambiarEstado(active,TenantContext.principalActual().usuarioId());try{almacenes.saveAndFlush(a);}catch(OptimisticLockingFailureException ex){throw concurrencia(ex);}auditoria.registrar(event,"Almacen",a.getId(),detalle(a));}
    private Almacen seguro(UUID id){if(id==null)throw new RecursoNoEncontradoException("Almacén no encontrado.");return almacenes.obtener(id,TenantContext.requerirTenantId(),EmpresaContext.requerirEmpresaId()).orElseThrow(()->new RecursoNoEncontradoException("Almacén no encontrado."));}
    private Sucursal sucursalSegura(UUID id,boolean active){Sucursal s=id==null?null:sucursales.findByIdAndEmpresaId(id,EmpresaContext.requerirEmpresaId()).orElse(null);if(s==null)throw new ReglaNegocioException("Selecciona una sucursal válida.");if(active&&!s.isActivo())throw new ReglaNegocioException("La sucursal seleccionada está inactiva.");return s;}
    private Usuario responsableSeguro(UUID id){if(id==null)return null;Usuario u=usuarios.findByIdAndTenantId(id,TenantContext.requerirTenantId()).orElseThrow(()->new ReglaNegocioException("Selecciona un responsable válido."));if(!u.getEmpresaId().equals(EmpresaContext.requerirEmpresaId())||!u.isActivo())throw new ReglaNegocioException("Selecciona un responsable activo de la empresa actual.");return u;}
    private void duplicado(UUID tenant,UUID empresa,UUID current,UUID branch,String name){boolean exists=current==null?almacenes.existsByTenantIdAndEmpresaIdAndSucursalIdAndNombreNormalizadoAndActivoTrue(tenant,empresa,branch,name):almacenes.existsByTenantIdAndEmpresaIdAndSucursalIdAndNombreNormalizadoAndActivoTrueAndIdNot(tenant,empresa,branch,name,current);if(exists)throw new ReglaNegocioException("Ya existe un almacén activo con este nombre en la sucursal seleccionada.");}
    static Validado validar(AlmacenInput input){if(input==null)throw new ReglaNegocioException("Los datos del almacén son obligatorios.");String name=requerido(input.nombre(),120,"El nombre es obligatorio.");if(input.sucursalId()==null)throw new ReglaNegocioException("La sucursal es obligatoria.");if(input.tipo()==null)throw new ReglaNegocioException("El tipo de almacén es obligatorio.");return new Validado(name,normalizar(name),input.sucursalId(),input.tipo(),input.responsableId(),opcional(input.direccion(),500,"La dirección excede 500 caracteres."),opcional(input.observacion(),1000,"La observación excede 1,000 caracteres."));}
    static String normalizar(String value){String clean=value==null?"":value.trim().replaceAll("\\s+"," ");return Normalizer.normalize(clean,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);}
    private static String requerido(String value,int max,String message){String clean=value==null?"":value.trim().replaceAll("\\s+"," ");if(clean.isEmpty())throw new ReglaNegocioException(message);if(clean.length()>max)throw new ReglaNegocioException("El nombre excede "+max+" caracteres.");return clean;}
    private static String opcional(String value,int max,String message){String clean=value==null?"":value.trim();if(clean.isEmpty())return null;if(clean.length()>max)throw new ReglaNegocioException(message);return clean;}
    private static void validarPagina(int page,int size){if(page<0||!PAGE_SIZES.contains(size))throw new ReglaNegocioException("Paginación inválida.");}
    private static void esperada(Almacen a,Long version){if(version==null||a.getVersion()!=version)throw concurrencia(null);}
    private static ReglaNegocioException concurrencia(Exception ex){return new ReglaNegocioException("El almacén fue modificado por otro usuario. Actualiza la información e inténtalo nuevamente.",ex);}
    private static ReglaNegocioException duplicado(DataIntegrityViolationException ex){return new ReglaNegocioException("Ya existe un almacén activo con este nombre en la sucursal seleccionada.",ex);}
    private static String detalle(Almacen a){return "{\"codigo\":\""+escape(a.getCodigo())+"\",\"nombre\":\""+escape(a.getNombre())+"\",\"sucursalId\":\""+a.getSucursalId()+"\",\"tipo\":\""+a.getTipo()+"\",\"activo\":"+a.isActivo()+"}";}
    private static String escape(String value){return value==null?"":value.replace("\\","\\\\").replace("\"","\\\"");}
    record Validado(String nombre,String nombreNormalizado,UUID sucursalId,TipoAlmacen tipo,UUID responsableId,String direccion,String observacion){}
}
