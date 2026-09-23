package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.dto.ModuloEmpresaDto;
import com.citacloud.springboot.contacloud.app.models.*;import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
import java.util.*;import java.util.stream.Collectors;
@Service
public class EmpresaModuloService {
    private final ModuloCatalogoRepository catalogo;private final EmpresaModuloRepository modulos;private final AuditoriaService auditoria;
    public EmpresaModuloService(ModuloCatalogoRepository catalogo,EmpresaModuloRepository modulos,AuditoriaService auditoria){this.catalogo=catalogo;this.modulos=modulos;this.auditoria=auditoria;}
    @Transactional(readOnly=true) public Set<String> habilitadosActuales(){var p=TenantContext.principalActual();Set<String> enabled=new HashSet<>(modulos.findEnabledKeys(p.tenantId(),p.empresaId()));catalogo.findAllByActiveTrueAndImplementedTrueOrderByDisplayOrder().stream().filter(ModuloCatalogo::isCore).map(ModuloCatalogo::getKey).forEach(enabled::add);return Set.copyOf(enabled);}
    @Transactional(readOnly=true) public boolean habilitado(String key){return habilitadosActuales().contains(normalizar(key));}
    @Transactional(readOnly=true) @PreAuthorize("hasAnyAuthority('empresas.ver','empresas.configurar_modulos')")
    public List<ModuloEmpresaDto> listar(UUID tenantId,UUID empresaId){validarContexto(tenantId);Set<String> enabled=modulos.findEnabledKeys(tenantId,empresaId);return catalogo.findAllByActiveTrueAndImplementedTrueOrderByDisplayOrder().stream().map(m->new ModuloEmpresaDto(m.getKey(),m.getName(),m.getDescription(),m.isCore(),m.isCore()||enabled.contains(m.getKey()))).toList();}
    @Transactional(readOnly=true) @PreAuthorize("hasAnyAuthority('empresas.ver','empresas.configurar_modulos')")
    public List<ModuloEmpresaDto> listarAdministracion(UUID tenantId,UUID empresaId){Set<String> enabled=modulos.findEnabledKeys(tenantId,empresaId);return catalogo.findAllByActiveTrueAndImplementedTrueOrderByDisplayOrder().stream().map(m->new ModuloEmpresaDto(m.getKey(),m.getName(),m.getDescription(),m.isCore(),m.isCore()||enabled.contains(m.getKey()))).toList();}
    @Transactional(readOnly=true) @PreAuthorize("hasAnyAuthority('empresas.crear','empresas.configurar_modulos')")
    public List<ModuloEmpresaDto> catalogoDisponible(){return catalogo.findAllByActiveTrueAndImplementedTrueOrderByDisplayOrder().stream().map(m->new ModuloEmpresaDto(m.getKey(),m.getName(),m.getDescription(),m.isCore(),m.isCore())).toList();}
    @Transactional @PreAuthorize("hasAuthority('empresas.configurar_modulos')")
    public void configurar(UUID tenantId,UUID empresaId,Set<String> keys){validarContexto(tenantId);aplicarConfiguracion(tenantId,empresaId,keys);auditoria.registrar("COMPANY_MODULES_UPDATED","Empresa",empresaId,"{}");}
    @Transactional @PreAuthorize("hasAuthority('empresas.configurar_modulos')")
    public void configurarAdministracion(UUID tenantId,UUID empresaId,Set<String> keys){aplicarConfiguracion(tenantId,empresaId,keys);auditoria.registrar("COMPANY_MODULES_UPDATED","Empresa",empresaId,"{}");}
    void configurarInicial(UUID tenantId,UUID empresaId,Set<String> keys,boolean puedeElegir){Set<String> seleccion=puedeElegir?keys:Set.of();aplicarConfiguracion(tenantId,empresaId,seleccion);}
    private void aplicarConfiguracion(UUID tenantId,UUID empresaId,Set<String> keys){Set<String> solicitados=keys==null?new HashSet<>():keys.stream().map(EmpresaModuloService::normalizar).collect(Collectors.toCollection(HashSet::new));List<ModuloCatalogo> disponibles=catalogo.findAllByActiveTrueAndImplementedTrueOrderByDisplayOrder();Set<String> validos=disponibles.stream().map(ModuloCatalogo::getKey).collect(Collectors.toSet());if(!validos.containsAll(solicitados))throw new ReglaNegocioException("La selección contiene módulos no disponibles.");
        disponibles.stream().filter(ModuloCatalogo::isCore).map(ModuloCatalogo::getKey).forEach(solicitados::add);for(ModuloCatalogo m:disponibles)if(solicitados.contains(m.getKey())&&m.getRequiredModuleKey()!=null&&!solicitados.contains(m.getRequiredModuleKey()))throw new ReglaNegocioException("El módulo "+m.getName()+" requiere "+m.getRequiredModuleKey()+".");
        Map<String,EmpresaModulo> existentes=modulos.findAllByTenantIdAndEmpresaId(tenantId,empresaId).stream().collect(Collectors.toMap(EmpresaModulo::getModuleKey,x->x));for(ModuloCatalogo m:disponibles){EmpresaModulo em=existentes.getOrDefault(m.getKey(),new EmpresaModulo(tenantId,empresaId,m.getKey(),false));em.setEnabled(solicitados.contains(m.getKey()));modulos.save(em);}}
    private static String normalizar(String key){return key==null?"":key.trim().toUpperCase(Locale.ROOT);}private static void validarContexto(UUID tenantId){if(!TenantContext.requerirTenantId().equals(tenantId))throw new ReglaNegocioException("El tenant solicitado no corresponde al contexto autenticado.");}
}
