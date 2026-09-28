package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.multitenancy.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@ConditionalOnProperty(name="contacloud.multidatabase.enabled",havingValue="true")
public class InfrastructureAdminService {
    private static final java.util.Set<Integer> PAGE_SIZES=java.util.Set.of(10,25,50,100);
    private final DirectoryGateway directory;
    private final String requiredSchemaVersion;

    public InfrastructureAdminService(DirectoryGateway directory,
            @Value("${contacloud.schema.required-version:}") String requiredSchemaVersion) {
        this.directory=directory;this.requiredSchemaVersion=clean(requiredSchemaVersion);
    }

    @PreAuthorize("hasAuthority('bases_datos.ver')")
    public DirectoryPage<DatabaseNodeDto> buscarBases(String query,DatabaseNodeType type,DatabaseNodeStatus status,int page,int size){
        validatePage(page,size);boolean canSeeDatabaseName=has("bases_datos.editar");DirectoryPage<DatabaseNode> result=directory.searchNodes(query,type,status,page,size,canSeeDatabaseName);
        return new DirectoryPage<>(result.content().stream().map(n->toDto(n,canSeeDatabaseName)).toList(),result.total());
    }

    @PreAuthorize("hasAuthority('bases_datos.ver')")
    public DatabaseNodeDto obtenerBase(UUID id){boolean canSeeDatabaseName=has("bases_datos.editar");return toDto(directory.findNode(id).orElseThrow(()->new RecursoNoEncontradoException("Base de datos no encontrada.")),canSeeDatabaseName);}

    @PreAuthorize("hasAuthority('bases_datos.crear')")
    public DatabaseNodeDto crearBase(DatabaseNodeInputDto input){validateNode(input);String code=clean(input.codigo()).toUpperCase(Locale.ROOT);UUID id=UUID.randomUUID();DatabaseNode node=new DatabaseNode(id,code,clean(input.nombre()),input.tipo(),input.estado(),clean(input.hostReference()),clean(input.nombreBase()),nullable(input.region()),input.tenantsMaximos(),0,input.umbralCapacidad(),nullable(input.versionSchema()),"env:CONTACLOUD_"+code.replaceAll("[^A-Z0-9]","_")+"_PASSWORD",input.saludable());return toDto(directory.createNode(node),true);}

    @PreAuthorize("hasAnyAuthority('bases_datos.editar','bases_datos.cambiar_estado')")
    public DatabaseNodeDto actualizarBase(UUID id,DatabaseNodeInputDto input){validateNode(input);DatabaseNode current=directory.findNode(id).orElseThrow(()->new RecursoNoEncontradoException("Base de datos no encontrada."));if(input.estado()!=current.status()&&!has("bases_datos.cambiar_estado"))throw new ReglaNegocioException("No tiene permiso para cambiar el estado de la base de datos.");if(!has("bases_datos.editar")&&!sameDefinition(current,input))throw new ReglaNegocioException("No tiene permiso para editar la configuración de la base de datos.");if(input.tenantsMaximos()<current.currentTenants())throw new ReglaNegocioException("La capacidad máxima no puede ser menor que los tenants actuales.");String host="conservar".equals(input.hostReference())?current.hostReference():clean(input.hostReference());String databaseName="conservar".equals(input.nombreBase())?current.databaseName():clean(input.nombreBase());DatabaseNode updated=new DatabaseNode(current.id(),current.code(),clean(input.nombre()),input.tipo(),input.estado(),host,databaseName,nullable(input.region()),input.tenantsMaximos(),current.currentTenants(),input.umbralCapacidad(),nullable(input.versionSchema()),current.secretReference(),input.saludable());return toDto(directory.updateNode(updated),true);}

    @PreAuthorize("hasAuthority('migraciones.ver')")
    public DirectoryPage<TenantMigrationDto> buscarMigraciones(String query,String status,int page,int size){validatePage(page,size);DirectoryPage<TenantMigration> result=directory.searchMigrations(query,status,page,size);return new DirectoryPage<>(result.content().stream().map(InfrastructureAdminService::toDto).toList(),result.total());}

    @PreAuthorize("hasAuthority('migraciones.ver')")
    public TenantMigrationDto obtenerMigracion(UUID id){return toDto(directory.findMigration(id));}

    @PreAuthorize("hasAuthority('migraciones.crear')")
    public List<TenantDirectoryOption> buscarTenants(String query){return directory.searchTenants(query,30);}

    @PreAuthorize("hasAuthority('migraciones.crear')")
    public List<DatabaseNodeDto> destinosElegibles(UUID tenantId){if(tenantId==null)return List.of();return directory.findEligibleMigrationTargets(tenantId,requiredSchemaVersion).stream().map(n->toDto(n,false)).toList();}

    @PreAuthorize("hasAuthority('migraciones.crear') and hasAuthority('migraciones.ejecutar')")
    public TenantMigrationDto programarMigracion(UUID tenantId,UUID targetNodeId){if(tenantId==null||targetNodeId==null)throw new ReglaNegocioException("Seleccione el tenant y la base de datos destino.");return toDto(directory.scheduleMigration(tenantId,targetNodeId,requiredSchemaVersion));}

    private static void validateNode(DatabaseNodeInputDto input){if(input==null||clean(input.codigo()).isEmpty()||clean(input.nombre()).isEmpty()||input.tipo()==null||input.estado()==null||clean(input.hostReference()).isEmpty()||clean(input.nombreBase()).isEmpty())throw new ReglaNegocioException("Código, nombre, tipo, estado, host y nombre de base son obligatorios.");if(!clean(input.codigo()).matches("[A-Za-z0-9._-]{2,30}"))throw new ReglaNegocioException("El código de base de datos no es válido.");if(input.tenantsMaximos()<1)throw new ReglaNegocioException("La capacidad debe ser mayor que cero.");BigDecimal threshold=input.umbralCapacidad();if(threshold==null||threshold.compareTo(BigDecimal.ZERO)<=0||threshold.compareTo(BigDecimal.valueOf(100))>0)throw new ReglaNegocioException("El umbral de capacidad debe estar entre 0 y 100.");}
    private static void validatePage(int page,int size){if(page<0||!PAGE_SIZES.contains(size))throw new IllegalArgumentException("Paginación inválida.");}
    private static boolean sameDefinition(DatabaseNode current,DatabaseNodeInputDto input){return clean(input.nombre()).equals(current.name())&&input.tipo()==current.type()&&(clean(input.hostReference()).equals("conservar")||clean(input.hostReference()).equals(current.hostReference()))&&(clean(input.nombreBase()).equals("conservar")||clean(input.nombreBase()).equals(current.databaseName()))&&java.util.Objects.equals(nullable(input.region()),current.region())&&input.tenantsMaximos()==current.maxTenants()&&input.umbralCapacidad().compareTo(current.capacityThreshold())==0&&java.util.Objects.equals(nullable(input.versionSchema()),current.schemaVersion())&&input.saludable()==current.healthy();}
    private static DatabaseNodeDto toDto(DatabaseNode n,boolean includeDatabaseName){return new DatabaseNodeDto(n.id(),n.code(),n.name(),n.type(),n.currentTenants(),n.maxTenants(),n.utilizationPercent(),n.capacityThreshold(),n.status(),n.schemaVersion(),n.healthy(),includeDatabaseName?n.databaseName():null,n.region());}
    private static TenantMigrationDto toDto(TenantMigration m){return new TenantMigrationDto(m.id(),m.tenantId(),m.tenantNombre(),m.sourceCode(),m.targetCode(),m.status(),m.startedAt(),m.completedAt(),m.createdAt());}
    private static boolean has(String authority){return TenantContext.principalActual().permisos().contains(authority);}
    private static String clean(String value){return value==null?"":value.trim();}
    private static String nullable(String value){String cleaned=clean(value);return cleaned.isEmpty()?null:cleaned;}
}
