package com.citacloud.springboot.contacloud.app.multitenancy;

import com.citacloud.springboot.contacloud.app.services.ReglaNegocioException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.ResultSet;
import java.util.*;

public class JdbcDirectoryGateway implements DirectoryGateway {
    private final JdbcTemplate jdbc; private final TransactionTemplate transactions;
    public JdbcDirectoryGateway(JdbcTemplate jdbc,TransactionTemplate transactions){this.jdbc=jdbc;this.transactions=transactions;}
    @Override public Optional<CompanyLocator> findCompanyByCode(String code){return jdbc.query("select empresa_id,tenant_id,codigo,activo from company_directory where upper(codigo)=upper(?)",(rs,n)->new CompanyLocator(uuid(rs,"empresa_id"),uuid(rs,"tenant_id"),rs.getString("codigo"),rs.getBoolean("activo")),code).stream().findFirst();}
    @Override public Optional<TenantRoute> findTenantRoute(UUID tenantId){return jdbc.query("""
        select td.tenant_id,td.status tenant_status,dn.* from tenant_directory td
        join database_node dn on dn.id=td.database_node_id where td.tenant_id=?
        """,(rs,n)->route(rs),tenantId).stream().findFirst();}
    @Override public Optional<ProvisioningLookup> findProvisioningByKey(String key){return jdbc.query("""
        select td.tenant_id,td.status,cd.empresa_id,cd.codigo,dn.code database_node_code
        from tenant_directory td join database_node dn on dn.id=td.database_node_id
        left join company_directory cd on cd.tenant_id=td.tenant_id
        where td.provisioning_key=?
        """,(rs,n)->new ProvisioningLookup(uuid(rs,"tenant_id"),TenantStatus.valueOf(rs.getString("status")),
            uuidNullable(rs,"empresa_id"),rs.getString("codigo"),rs.getString("database_node_code")),key).stream().findFirst();}
    @Override public Optional<DatabaseNode> findNode(UUID nodeId){return jdbc.query("select * from database_node where id=?",(rs,n)->node(rs),nodeId).stream().findFirst();}
    @Override public List<DatabaseNode> findEligibleSharedNodes(String schema){return jdbc.query("""
        select * from database_node where type='COMPARTIDA' and status='ACTIVA' and healthy=true
        and current_tenants<max_tenants and (current_tenants*100.0/max_tenants)<capacity_threshold
        and (?='' or schema_version=?) order by (current_tenants*1.0/max_tenants),code
        """,(rs,n)->node(rs),safe(schema),safe(schema));}
    @Override public List<DatabaseNode> findEligibleDedicatedNodes(String schema){return jdbc.query("""
        select * from database_node where type='DEDICADA' and status='ACTIVA' and healthy=true
        and current_tenants=0 and (?='' or schema_version=?) order by code
        """,(rs,n)->node(rs),safe(schema),safe(schema));}
    @Override public DatabaseNode reserveAutomatic(String schema){return Objects.requireNonNull(transactions.execute(status->{
        List<DatabaseNode> candidates=jdbc.query("""
            select * from database_node where type='COMPARTIDA' and status='ACTIVA' and healthy=true
            and current_tenants<max_tenants and (current_tenants*100.0/max_tenants)<capacity_threshold
            and (?='' or schema_version=?) order by (current_tenants*1.0/max_tenants),code for update skip locked limit 1
            """,(rs,n)->node(rs),safe(schema),safe(schema));
        if(candidates.isEmpty())throw new ReglaNegocioException("No hay bases de datos disponibles para alojar el tenant.");
        DatabaseNode selected=candidates.getFirst();increment(selected.id());return withCount(selected,selected.currentTenants()+1);
    }));}
    @Override public DatabaseNode reserveManual(UUID nodeId,DatabaseNodeType requiredType,String schema){return Objects.requireNonNull(transactions.execute(status->{
        List<DatabaseNode> nodes=jdbc.query("select * from database_node where id=? for update",(rs,n)->node(rs),nodeId);
        if(nodes.isEmpty())throw new ReglaNegocioException("La base de datos seleccionada no existe.");DatabaseNode selected=nodes.getFirst();
        if(selected.type()!=requiredType||!selected.acceptsNewTenant()||(!safe(schema).isEmpty()&&!safe(schema).equals(selected.schemaVersion())))
            throw new ReglaNegocioException("La base de datos seleccionada ya no está disponible.");
        increment(selected.id());return withCount(selected,selected.currentTenants()+1);
    }));}
    @Override public void releaseReservation(UUID nodeId){jdbc.update("update database_node set current_tenants=greatest(current_tenants-1,0),version=version+1,updated_at=current_timestamp where id=?",nodeId);}
    @Override public void createProvisioningTenant(UUID tenantId,UUID nodeId,DatabaseNodeType hostingType,String key){jdbc.update("""
        insert into tenant_directory(tenant_id,database_node_id,hosting_type,status,provisioning_key)
        values (?,?,?,'PROVISIONING',?)
        """,tenantId,nodeId,hostingType.name(),key);}
    @Override public void markTenantStatus(UUID tenantId,TenantStatus status){jdbc.update("update tenant_directory set status=?,version=version+1,updated_at=current_timestamp where tenant_id=?",status.name(),tenantId);}
    @Override public void registerCompany(UUID empresaId,UUID tenantId,String codigo,String nombre){jdbc.update("""
        insert into company_directory(empresa_id,tenant_id,codigo,nombre,activo) values (?,?,?,?,true)
        on conflict(empresa_id) do update set codigo=excluded.codigo,nombre=excluded.nombre,activo=true,updated_at=current_timestamp
        """,empresaId,tenantId,codigo,nombre);}
    private void increment(UUID id){int changed=jdbc.update("""
        update database_node set current_tenants=current_tenants+1,version=version+1,updated_at=current_timestamp
        where id=? and status='ACTIVA' and healthy=true and current_tenants<max_tenants
        and (current_tenants*100.0/max_tenants)<capacity_threshold
        """,id);if(changed!=1)throw new ReglaNegocioException("La capacidad de la base cambió durante la asignación.");}
    private static TenantRoute route(ResultSet rs)throws java.sql.SQLException{return new TenantRoute(uuid(rs,"tenant_id"),TenantStatus.valueOf(rs.getString("tenant_status")),node(rs));}
    private static DatabaseNode node(ResultSet rs)throws java.sql.SQLException{return new DatabaseNode(uuid(rs,"id"),rs.getString("code"),rs.getString("name"),DatabaseNodeType.valueOf(rs.getString("type")),DatabaseNodeStatus.valueOf(rs.getString("status")),rs.getString("host_reference"),rs.getString("database_name"),rs.getString("region"),rs.getInt("max_tenants"),rs.getInt("current_tenants"),rs.getBigDecimal("capacity_threshold"),rs.getString("schema_version"),rs.getString("secret_reference"),rs.getBoolean("healthy"));}
    private static UUID uuid(ResultSet rs,String column)throws java.sql.SQLException{return rs.getObject(column,UUID.class);}
    private static UUID uuidNullable(ResultSet rs,String column)throws java.sql.SQLException{return rs.getObject(column,UUID.class);}
    private static String safe(String value){return value==null?"":value.trim();}
    private static DatabaseNode withCount(DatabaseNode n,int count){return new DatabaseNode(n.id(),n.code(),n.name(),n.type(),n.status(),n.hostReference(),n.databaseName(),n.region(),n.maxTenants(),count,n.capacityThreshold(),n.schemaVersion(),n.secretReference(),n.healthy());}
}
