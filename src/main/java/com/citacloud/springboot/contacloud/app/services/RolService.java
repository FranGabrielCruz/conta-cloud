package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.RolMapper;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RolService {
    private static final Set<Integer> TAMANOS = Set.of(10, 25, 50, 100);
    private final RolRepository roles; private final PermisoRepository permisos;
    private final UsuarioEmpresaRepository accesos; private final RolMapper mapper;
    private final RolPermisoRepository rolPermisos; private final EntityManager entityManager; private final AuditoriaService auditoria;
    private final EmpresaModuloService modulos;
    public RolService(RolRepository roles, PermisoRepository permisos, UsuarioEmpresaRepository accesos,
                      RolMapper mapper, RolPermisoRepository rolPermisos, EntityManager entityManager,
                      AuditoriaService auditoria, EmpresaModuloService modulos) {
        this.roles=roles; this.permisos=permisos; this.accesos=accesos; this.mapper=mapper;
        this.rolPermisos=rolPermisos; this.entityManager=entityManager; this.auditoria=auditoria; this.modulos=modulos;
    }

    @Transactional(readOnly=true)
    @PreAuthorize("hasAnyAuthority('ROL_VER','roles.ver')")
    public Page<RolDto> buscar(String texto, Boolean activo, int pagina, int tamano) {
        if (pagina < 0 || !TAMANOS.contains(tamano)) throw new IllegalArgumentException("Paginación inválida.");
        UUID empresaId=TenantContext.requerirEmpresaId();
        Pageable pageable=PageRequest.of(pagina,tamano,Sort.by("nombre").ascending());
        Page<Rol> page=activo==null?roles.buscar(empresaId,limpiar(texto),pageable):roles.buscarPorEstado(empresaId,limpiar(texto),activo,pageable);
        List<UUID> ids=page.getContent().stream().map(Rol::getId).toList();
        Map<UUID,Long> cantidades=ids.isEmpty()?Map.of():accesos.contarPorRoles(empresaId,ids).stream()
            .collect(Collectors.toMap(UsuarioEmpresaRepository.RolCantidad::getRolId,UsuarioEmpresaRepository.RolCantidad::getCantidad));
        return page.map(r->mapper.toSummaryDto(r,cantidades.getOrDefault(r.getId(),0L)));
    }

    @Transactional(readOnly=true)
    @PreAuthorize("hasAnyAuthority('ROL_VER','roles.ver')")
    public RolDto obtener(UUID id) { Rol r=rolSeguro(id); return mapper.toDto(r,accesos.countByEmpresaIdAndRolIdAndActivoTrue(r.getEmpresaId(),r.getId())); }

    @Transactional(readOnly=true)
    @PreAuthorize("hasAnyAuthority('ROL_VER','roles.ver','ROL_CREAR','roles.crear','ROL_EDITAR','roles.editar')")
    public List<PermisoDto> catalogoPermisos() { return permisosDisponibles().stream().map(mapper::toDto).toList(); }

    @Transactional
    @PreAuthorize("hasAnyAuthority('ROL_CREAR','roles.crear')")
    public RolDto crear(RolInputDto input) {
        String nombre=validarNombre(input,null); UUID empresaId=TenantContext.requerirEmpresaId();
        Set<UUID> permisoIds=validarPermisos(input.permisoIds());
        Rol rol=new Rol(empresaId,"ROL-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT),nombre);
        rol.setDescripcion(limitar(input.descripcion(),255)); rol.setActivo(input.activo()); roles.saveAndFlush(rol);
        reemplazarPermisos(empresaId,rol.getId(),permisoIds); entityManager.clear();
        auditoria.registrar("ROL_CREADO","Rol",rol.getId(),"{}"); return obtenerInterno(rol.getId());
    }

    @Transactional
    @PreAuthorize("hasAnyAuthority('ROL_EDITAR','roles.editar')")
    public RolDto actualizar(UUID id,RolInputDto input) {
        Rol rol=rolSeguro(id); String nombre=validarNombre(input,id);
        if(rol.isProtegido() && (!rol.getNombre().equalsIgnoreCase(nombre)||!input.activo()))
            throw new ReglaNegocioException("El rol Administrador es protegido y no puede renombrarse ni desactivarse.");
        Set<UUID> permisoIds=validarPermisos(input.permisoIds()); rol.setNombre(nombre);
        rol.setDescripcion(limitar(input.descripcion(),255)); rol.setActivo(input.activo()); roles.saveAndFlush(rol);
        reemplazarPermisos(rol.getEmpresaId(),rol.getId(),permisoIds); entityManager.clear();
        auditoria.registrar("ROL_EDITADO","Rol",rol.getId(),"{}"); return obtenerInterno(id);
    }

    @Transactional
    @PreAuthorize("hasAnyAuthority('ROL_DESACTIVAR','roles.desactivar')")
    public void desactivar(UUID id) {
        Rol rol=rolSeguro(id); if(rol.isProtegido()) throw new ReglaNegocioException("El rol Administrador no puede desactivarse.");
        long asignados=accesos.countByEmpresaIdAndRolIdAndActivoTrue(rol.getEmpresaId(),rol.getId());
        if(asignados>0) throw new ReglaNegocioException("No es posible desactivar el rol porque tiene usuarios activos asignados.");
        rol.setActivo(false); roles.save(rol); auditoria.registrar("ROL_DESACTIVADO","Rol",rol.getId(),"{}");
    }

    private RolDto obtenerInterno(UUID id){ Rol r=rolSeguro(id); return mapper.toDto(r,accesos.countByEmpresaIdAndRolIdAndActivoTrue(r.getEmpresaId(),r.getId())); }
    private Rol rolSeguro(UUID id){ return roles.findByIdAndEmpresaId(id,TenantContext.requerirEmpresaId()).orElseThrow(()->new RecursoNoEncontradoException("Rol no encontrado.")); }
    private String validarNombre(RolInputDto input,UUID id){ if(input==null)throw new ReglaNegocioException("Los datos del rol son obligatorios."); String n=limpiar(input.nombre());
        if(n.isEmpty()||n.length()>100)throw new ReglaNegocioException("El nombre del rol es obligatorio y no debe exceder 100 caracteres.");
        boolean existe=id==null?roles.existsByEmpresaIdAndNombreIgnoreCase(TenantContext.requerirEmpresaId(),n):roles.existsByEmpresaIdAndNombreIgnoreCaseAndIdNot(TenantContext.requerirEmpresaId(),n,id);
        if(existe)throw new ReglaNegocioException("Ya existe un rol con ese nombre en la empresa."); return n; }
    private Set<UUID> validarPermisos(Set<UUID> ids){Set<UUID> seguros=ids==null?Set.of():Set.copyOf(ids);List<Permiso> seleccionados=permisos.findAllByIdIn(seguros);Set<String> habilitados=modulos.habilitadosActuales();if(seleccionados.size()!=seguros.size()||seleccionados.stream().anyMatch(p->!disponible(p,habilitados)))throw new ReglaNegocioException("La selección contiene permisos de módulos no habilitados para la empresa.");return seguros;}
    private List<Permiso> permisosDisponibles(){Set<String> habilitados=modulos.habilitadosActuales();return permisos.findAllByOrderByModuloAscRecursoAscCodigoAsc().stream().filter(p->disponible(p,habilitados)).toList();}
    private static boolean disponible(Permiso permiso,Set<String> habilitados){return PermisoTenantPolicy.disponibleParaTenant(permiso.getModulo(),permiso.getRecurso())&&PermisoModuloPolicy.moduloRequerido(permiso.getCodigo(),permiso.getRecurso()).filter(habilitados::contains).isPresent();}
    private void reemplazarPermisos(UUID empresaId,UUID rolId,Set<UUID> ids){rolPermisos.deleteAllByRolId(rolId);UUID tenantId=TenantContext.requerirTenantId();rolPermisos.saveAll(ids.stream().map(id->new RolPermiso(tenantId,empresaId,rolId,id)).toList());}
    private static String limpiar(String s){return s==null?"":s.trim();}
    private static String limitar(String s,int max){String v=limpiar(s);if(v.length()>max)throw new ReglaNegocioException("La descripción excede "+max+" caracteres.");return v.isEmpty()?null:v;}
}
