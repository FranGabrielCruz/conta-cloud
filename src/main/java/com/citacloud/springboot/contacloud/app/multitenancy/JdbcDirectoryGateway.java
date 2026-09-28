package com.citacloud.springboot.contacloud.app.multitenancy;

import com.citacloud.springboot.contacloud.app.services.ReglaNegocioException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.ResultSet;
import java.sql.Timestamp;
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
    @Override public DirectoryPage<DatabaseNode> searchNodes(String query,DatabaseNodeType type,DatabaseNodeStatus status,int page,int size,boolean searchDatabaseName){
        String term=safe(query);List<Object> args=new ArrayList<>();StringBuilder where=new StringBuilder(" where 1=1");
        if(!term.isEmpty()){where.append(searchDatabaseName?" and (lower(code) like lower(?) or lower(name) like lower(?) or lower(database_name) like lower(?))":" and (lower(code) like lower(?) or lower(name) like lower(?))");String like="%"+term+"%";args.add(like);args.add(like);if(searchDatabaseName)args.add(like);}
        if(type!=null){where.append(" and type=?");args.add(type.name());}if(status!=null){where.append(" and status=?");args.add(status.name());}
        long total=Objects.requireNonNull(jdbc.queryForObject("select count(*) from database_node"+where,Long.class,args.toArray()));
        List<Object> pageArgs=new ArrayList<>(args);pageArgs.add(size);pageArgs.add((long)page*size);
        List<DatabaseNode> content=jdbc.query("select * from database_node"+where+" order by code limit ? offset ?",(rs,n)->node(rs),pageArgs.toArray());
        return new DirectoryPage<>(content,total);
    }
    @Override public DatabaseNode createNode(DatabaseNode node){jdbc.update("""
        insert into database_node(id,code,name,type,status,host_reference,database_name,region,max_tenants,current_tenants,
        capacity_threshold,schema_version,secret_reference,healthy) values (?,?,?,?,?,?,?,?,?,0,?,?,?,?)
        """,node.id(),node.code(),node.name(),node.type().name(),node.status().name(),node.hostReference(),node.databaseName(),node.region(),node.maxTenants(),node.capacityThreshold(),node.schemaVersion(),node.secretReference(),node.healthy());return findNode(node.id()).orElseThrow();}
    @Override public DatabaseNode updateNode(DatabaseNode node){int changed=jdbc.update("""
        update database_node set name=?,type=?,status=?,host_reference=?,database_name=?,region=?,max_tenants=?,
        capacity_threshold=?,schema_version=?,healthy=?,version=version+1,updated_at=current_timestamp where id=?
        """,node.name(),node.type().name(),node.status().name(),node.hostReference(),node.databaseName(),node.region(),node.maxTenants(),node.capacityThreshold(),node.schemaVersion(),node.healthy(),node.id());
        if(changed!=1)throw new ReglaNegocioException("La base de datos no existe.");return findNode(node.id()).orElseThrow();}
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
    @Override public void setCompanyActive(UUID empresaId,boolean active){jdbc.update("update company_directory set activo=?,updated_at=current_timestamp where empresa_id=?",active,empresaId);}
    @Override public List<TenantDirectoryOption> searchTenants(String query,int limit){String term="%"+safe(query)+"%";return jdbc.query("""
        select td.tenant_id,coalesce(string_agg(cd.nombre, ', ' order by cd.nombre),td.tenant_id::text) tenant_name,dn.*
        from tenant_directory td join database_node dn on dn.id=td.database_node_id
        left join company_directory cd on cd.tenant_id=td.tenant_id
        where td.status='ACTIVE' and (?='%%' or cast(td.tenant_id as text) like ? or exists(
            select 1 from company_directory f where f.tenant_id=td.tenant_id
            and (lower(f.nombre) like lower(?) or lower(f.codigo) like lower(?))))
        group by td.tenant_id,dn.id order by tenant_name limit ?
        """,(rs,n)->new TenantDirectoryOption(uuid(rs,"tenant_id"),rs.getString("tenant_name"),node(rs)),term,term,term,term,limit);}
    @Override public List<DatabaseNode> findEligibleMigrationTargets(UUID tenantId,String schema){TenantRoute route=findTenantRoute(tenantId).orElseThrow(()->new ReglaNegocioException("Tenant no encontrado."));return jdbc.query("""
        select * from database_node where id<>? and status='ACTIVA' and healthy=true and current_tenants<max_tenants
        and (current_tenants*100.0/max_tenants)<capacity_threshold and (type='COMPARTIDA' or current_tenants=0)
        and (?='' or schema_version=?) order by code
        """,(rs,n)->node(rs),route.databaseNode().id(),safe(schema),safe(schema));}
    @Override public DirectoryPage<TenantMigration> searchMigrations(String query,String status,int page,int size){String term=safe(query);List<Object> args=new ArrayList<>();StringBuilder where=new StringBuilder(" where 1=1");if(!term.isEmpty()){where.append(" and (cast(tm.tenant_id as text) like ? or lower(coalesce(c.tenant_name,'')) like lower(?) or lower(src.code) like lower(?) or lower(dst.code) like lower(?))");String like="%"+term+"%";args.add(like);args.add(like);args.add(like);args.add(like);}if(status!=null&&!status.isBlank()){where.append(" and tm.status=?");args.add(status);}
        String joins=" from tenant_migration tm join database_node src on src.id=tm.source_database_node_id join database_node dst on dst.id=tm.target_database_node_id left join (select tenant_id,string_agg(nombre, ', ' order by nombre) tenant_name from company_directory group by tenant_id)c on c.tenant_id=tm.tenant_id";
        long total=Objects.requireNonNull(jdbc.queryForObject("select count(*)"+joins+where,Long.class,args.toArray()));List<Object> pageArgs=new ArrayList<>(args);pageArgs.add(size);pageArgs.add((long)page*size);
        List<TenantMigration> content=jdbc.query("select tm.*,coalesce(c.tenant_name,tm.tenant_id::text) tenant_name,src.code source_code,dst.code target_code"+joins+where+" order by tm.created_at desc limit ? offset ?",(rs,n)->migration(rs),pageArgs.toArray());return new DirectoryPage<>(content,total);}
    @Override public TenantMigration findMigration(UUID migrationId){return jdbc.query("""
        select tm.*,coalesce(c.tenant_name,tm.tenant_id::text) tenant_name,src.code source_code,dst.code target_code
        from tenant_migration tm join database_node src on src.id=tm.source_database_node_id join database_node dst on dst.id=tm.target_database_node_id
        left join (select tenant_id,string_agg(nombre, ', ' order by nombre) tenant_name from company_directory group by tenant_id)c on c.tenant_id=tm.tenant_id where tm.id=?
        """,(rs,n)->migration(rs),migrationId).stream().findFirst().orElseThrow(()->new ReglaNegocioException("Migración no encontrada."));}
    @Override public TenantMigration scheduleMigration(UUID tenantId,UUID targetNodeId,String schema){UUID id=UUID.randomUUID();transactions.executeWithoutResult(tx->{List<TenantRoute> routes=jdbc.query("""
            select td.tenant_id,td.status tenant_status,dn.* from tenant_directory td join database_node dn on dn.id=td.database_node_id where td.tenant_id=? for update of td
            """,(rs,n)->route(rs),tenantId);if(routes.isEmpty())throw new ReglaNegocioException("Tenant no encontrado.");TenantRoute route=routes.getFirst();if(route.tenantStatus()!=TenantStatus.ACTIVE)throw new ReglaNegocioException("El tenant no está disponible para migración.");
        List<DatabaseNode> targets=jdbc.query("select * from database_node where id=? for update",(rs,n)->node(rs),targetNodeId);if(targets.isEmpty())throw new ReglaNegocioException("La base de datos destino no existe.");DatabaseNode target=targets.getFirst();if(target.id().equals(route.databaseNode().id())||!target.acceptsNewTenant()||(target.type()==DatabaseNodeType.DEDICADA&&target.currentTenants()>0)||(!safe(schema).isEmpty()&&!safe(schema).equals(target.schemaVersion())))throw new ReglaNegocioException("La base de datos destino ya no es elegible.");
        increment(target.id());jdbc.update("insert into tenant_migration(id,tenant_id,source_database_node_id,target_database_node_id,status) values (?,?,?,?,'PENDIENTE')",id,tenantId,route.databaseNode().id(),target.id());jdbc.update("update tenant_directory set status='MIGRATING',target_database_node_id=?,migration_status='PENDIENTE',version=version+1,updated_at=current_timestamp where tenant_id=?",target.id(),tenantId);});return findMigration(id);}
    private void increment(UUID id){int changed=jdbc.update("""
        update database_node set current_tenants=current_tenants+1,version=version+1,updated_at=current_timestamp
        where id=? and status='ACTIVA' and healthy=true and current_tenants<max_tenants
        and (current_tenants*100.0/max_tenants)<capacity_threshold
        """,id);if(changed!=1)throw new ReglaNegocioException("La capacidad de la base cambió durante la asignación.");}
    private static TenantRoute route(ResultSet rs)throws java.sql.SQLException{return new TenantRoute(uuid(rs,"tenant_id"),TenantStatus.valueOf(rs.getString("tenant_status")),node(rs));}
    private static DatabaseNode node(ResultSet rs)throws java.sql.SQLException{return new DatabaseNode(uuid(rs,"id"),rs.getString("code"),rs.getString("name"),DatabaseNodeType.valueOf(rs.getString("type")),DatabaseNodeStatus.valueOf(rs.getString("status")),rs.getString("host_reference"),rs.getString("database_name"),rs.getString("region"),rs.getInt("max_tenants"),rs.getInt("current_tenants"),rs.getBigDecimal("capacity_threshold"),rs.getString("schema_version"),rs.getString("secret_reference"),rs.getBoolean("healthy"));}
    private static TenantMigration migration(ResultSet rs)throws java.sql.SQLException{return new TenantMigration(uuid(rs,"id"),uuid(rs,"tenant_id"),rs.getString("tenant_name"),rs.getString("source_code"),rs.getString("target_code"),rs.getString("status"),time(rs,"started_at"),time(rs,"completed_at"),time(rs,"created_at"));}
    private static java.time.OffsetDateTime time(ResultSet rs,String column)throws java.sql.SQLException{Timestamp value=rs.getTimestamp(column);return value==null?null:value.toInstant().atOffset(java.time.ZoneOffset.UTC);}
    private static UUID uuid(ResultSet rs,String column)throws java.sql.SQLException{return rs.getObject(column,UUID.class);}
    private static UUID uuidNullable(ResultSet rs,String column)throws java.sql.SQLException{return rs.getObject(column,UUID.class);}
    private static String safe(String value){return value==null?"":value.trim();}
    private static DatabaseNode withCount(DatabaseNode n,int count){return new DatabaseNode(n.id(),n.code(),n.name(),n.type(),n.status(),n.hostReference(),n.databaseName(),n.region(),n.maxTenants(),count,n.capacityThreshold(),n.schemaVersion(),n.secretReference(),n.healthy());}
}
