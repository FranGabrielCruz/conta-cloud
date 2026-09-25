package com.citacloud.springboot.contacloud.app.multitenancy;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.services.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

public class TenantProvisioningService {
    private final DirectoryGateway directory; private final DatabaseAllocationService allocation;
    private final DataSourceRegistry registry; private final TenantDatabaseResolver routing;
    private final PasswordEncoder passwordEncoder;
    public TenantProvisioningService(DirectoryGateway directory,DatabaseAllocationService allocation,
            DataSourceRegistry registry,TenantDatabaseResolver routing,PasswordEncoder passwordEncoder){
        this.directory=directory;this.allocation=allocation;this.registry=registry;this.routing=routing;this.passwordEncoder=passwordEncoder;
    }
    @PreAuthorize("hasAuthority('empresas.crear')")
    public ProvisioningResult provision(ProvisionTenantRequest request,UsuarioInicialDto usuarioInicial,String clave,String confirmarClave){
        validar(request);String code=CodigoEmpresaValidator.validarYNormalizar(request.empresa().codigo());UsuarioInicialDto usuarioValidado=UsuarioInicialValidator.validar(usuarioInicial,clave,confirmarClave);var actor=TenantContext.principalActual();
        Optional<ProvisioningLookup> previous=directory.findProvisioningByKey(request.idempotencyKey());
        if(previous.isPresent()){
            ProvisioningLookup lookup=previous.get();
            if(lookup.completed())return new ProvisioningResult(lookup.tenantId(),lookup.empresaId(),lookup.empresaCodigo(),lookup.databaseNodeCode(),lookup.status().name());
            throw new ReglaNegocioException("Ya existe un provisioning con esta clave en estado "+lookup.status()+". No se crearán datos duplicados.");
        }
        if(directory.findCompanyByCode(code).isPresent())throw new ReglaNegocioException("El código de acceso ya está siendo utilizado por otra empresa.");
        if(request.hostingMode()!=HostingMode.AUTOMATIC&&!actor.permisos().contains("empresas.seleccionar_base"))
            throw new ReglaNegocioException("No tiene permiso para seleccionar una base de datos.");
        if(request.moduleKeys()!=null&&!request.moduleKeys().isEmpty()&&!actor.permisos().contains("empresas.configurar_modulos"))
            throw new ReglaNegocioException("No tiene permiso para configurar módulos.");
        String passwordHash=passwordEncoder.encode(clave);
        DatabaseNode node=allocation.reserve(request.hostingMode(),request.databaseNodeId());
        UUID tenantId=UUID.randomUUID(),empresaId=UUID.randomUUID(),rolId=UUID.randomUUID(),usuarioId=UUID.randomUUID(),accesoId=UUID.randomUUID();
        DatabaseNodeType hosting=request.hostingMode()==HostingMode.DEDICATED?DatabaseNodeType.DEDICADA:DatabaseNodeType.COMPARTIDA;
        try{
            directory.createProvisioningTenant(tenantId,node.id(),hosting,request.idempotencyKey());
            var targetDataSource=registry.get(node);JdbcTemplate target=new JdbcTemplate(targetDataSource);
            new TransactionTemplate(new DataSourceTransactionManager(targetDataSource)).executeWithoutResult(status->
                createOperational(target,tenantId,empresaId,rolId,usuarioId,accesoId,code,request,usuarioValidado,passwordHash));
            directory.registerCompany(empresaId,tenantId,code,request.empresa().nombreComercial().trim());
            target.update("update empresas set activo=true where id=? and tenant_id=?",empresaId,tenantId);
            directory.markTenantStatus(tenantId,TenantStatus.ACTIVE);routing.invalidate(tenantId);
            return new ProvisioningResult(tenantId,empresaId,code,node.code(),TenantStatus.ACTIVE.name());
        }catch(RuntimeException ex){
            try{directory.markTenantStatus(tenantId,TenantStatus.FAILED);}catch(RuntimeException ignored){}
            try{allocation.release(node.id());}catch(RuntimeException ignored){}
            routing.invalidate(tenantId);throw new ReglaNegocioException("No fue posible completar el provisioning del tenant.",ex);
        }
    }
    private static void createOperational(JdbcTemplate jdbc,UUID tenantId,UUID empresaId,UUID rolId,
            UUID usuarioId,UUID accesoId,String code,ProvisionTenantRequest request,UsuarioInicialDto initial,String passwordHash){
        NuevaEmpresaDto e=request.empresa();Integer limiteUsuarios=LimiteUsuariosValidator.normalizar(e.limiteUsuariosHabilitado(),e.limiteUsuarios());
        jdbc.update("insert into empresas(id,tenant_id,codigo,nombre,identificacion_fiscal,activo,limite_usuarios_habilitado,limite_usuarios) values (?,?,?,?,?,false,?,?)",empresaId,tenantId,code,e.nombreComercial().trim(),CompanyConfigurationService.normalizeRnc(e.identificacionFiscal()),e.limiteUsuariosHabilitado(),limiteUsuarios);
        jdbc.update("insert into datos_empresa(empresa_id,nombre_comercial,razon_social,direccion,telefono,correo) values (?,?,?,?,?,?)",empresaId,e.nombreComercial().trim(),blank(e.razonSocial()),blank(e.direccion()),CompanyConfigurationService.normalizePhone(e.telefono()),CompanyConfigurationService.normalizeEmail(e.correo()));
        jdbc.update("insert into sucursales(empresa_id,codigo,nombre,principal,activo) values (?,?,?,true,true)",empresaId,"PRINCIPAL","Sucursal Principal");
        jdbc.update("insert into monedas(empresa_id,codigo_iso,nombre,simbolo,decimales,moneda_base,activo) values (?,?,?,?,2,true,true)",empresaId,"DOP","Peso dominicano","RD$");
        jdbc.update("insert into roles(id,empresa_id,codigo,nombre,protegido,activo) values (?,?,?,?,true,true)",rolId,empresaId,"ADMINISTRADOR","Administrador");
        List<PermisoProvisionable> permisos=jdbc.query("select id,modulo,recurso from permisos",
            (rs,n)->new PermisoProvisionable(rs.getObject("id",UUID.class),rs.getString("modulo"),rs.getString("recurso")));
        permisos.stream().filter(p->PermisoTenantPolicy.disponibleParaAdministradorInicial(p.modulo(),p.recurso()))
            .forEach(p->jdbc.update("insert into rol_permisos(empresa_id,rol_id,permiso_id) values (?,?,?)",empresaId,rolId,p.id()));
        jdbc.update("insert into usuarios(id,tenant_id,empresa_id,usuario,nombre,apellido,correo,telefono,password_hash,activo) values (?,?,?,?,?,?,?,?,?,true)",usuarioId,tenantId,empresaId,initial.usuario(),initial.nombre(),initial.apellido(),initial.correo(),initial.telefono(),passwordHash);
        jdbc.update("insert into usuario_empresa(id,usuario_id,empresa_id,rol_id,activo,acceso_todas_sucursales) values (?,?,?,?,true,true)",accesoId,usuarioId,empresaId,rolId);
        Set<String> requested=request.moduleKeys()==null?new HashSet<>():new HashSet<>(request.moduleKeys().stream().map(x->x.trim().toUpperCase(Locale.ROOT)).toList());
        List<String> available=jdbc.queryForList("select module_key from module_catalog where active=true and implemented=true",String.class);
        if(!new HashSet<>(available).containsAll(requested))throw new ReglaNegocioException("La selección contiene módulos no disponibles.");
        requested.addAll(jdbc.queryForList("select module_key from module_catalog where active=true and implemented=true and core=true",String.class));
        for(String key:available)jdbc.update("insert into empresa_modulos(tenant_id,empresa_id,module_key,enabled) values (?,?,?,?)",tenantId,empresaId,key,requested.contains(key));
        jdbc.update("insert into auditoria(empresa_id,usuario_id,accion,entidad,registro_id,detalle) values (?,?,?,?,?,cast(? as jsonb))",empresaId,usuarioId,"TENANT_CREATED","Tenant",tenantId,"{}");
    }
    private static void validar(ProvisionTenantRequest r){
        if(r==null||r.empresa()==null||r.empresa().nombreComercial()==null||r.empresa().nombreComercial().isBlank())throw new ReglaNegocioException("El nombre comercial es obligatorio.");
        if(r.idempotencyKey()==null||r.idempotencyKey().isBlank()||r.idempotencyKey().length()>120)throw new ReglaNegocioException("La clave de idempotencia es obligatoria.");
        if(r.hostingMode()==null)throw new ReglaNegocioException("Seleccione el modo de alojamiento.");
        if(r.hostingMode()==HostingMode.AUTOMATIC&&r.databaseNodeId()!=null)throw new ReglaNegocioException("La asignación automática no acepta una base seleccionada.");
    }
    static boolean permisoDisponibleParaTenant(String modulo,String recurso){return PermisoTenantPolicy.disponibleParaAdministradorInicial(modulo,recurso);}
    private record PermisoProvisionable(UUID id,String modulo,String recurso){}
    private static String blank(String s){return s==null||s.isBlank()?null:s.trim();}
}
