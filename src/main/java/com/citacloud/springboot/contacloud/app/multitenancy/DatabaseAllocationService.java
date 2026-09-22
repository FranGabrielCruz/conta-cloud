package com.citacloud.springboot.contacloud.app.multitenancy;
import java.util.*;
public class DatabaseAllocationService {
    private final DirectoryGateway directory; private final String requiredSchemaVersion;
    public DatabaseAllocationService(DirectoryGateway directory,String requiredSchemaVersion){this.directory=directory;this.requiredSchemaVersion=requiredSchemaVersion==null?"":requiredSchemaVersion.trim();}
    public List<DatabaseNode> eligibleSharedNodes(){return directory.findEligibleSharedNodes(requiredSchemaVersion);}
    public List<DatabaseNode> eligibleDedicatedNodes(){return directory.findEligibleDedicatedNodes(requiredSchemaVersion);}
    public DatabaseNode reserve(HostingMode mode,UUID requestedNodeId){return switch(mode){
        case AUTOMATIC->directory.reserveAutomatic(requiredSchemaVersion);
        case MANUAL->{if(requestedNodeId==null)throw new IllegalArgumentException("Debe seleccionar una base de datos.");yield directory.reserveManual(requestedNodeId,DatabaseNodeType.COMPARTIDA,requiredSchemaVersion);}
        case DEDICATED->{if(requestedNodeId==null)throw new IllegalArgumentException("Debe seleccionar una base dedicada disponible.");yield directory.reserveManual(requestedNodeId,DatabaseNodeType.DEDICADA,requiredSchemaVersion);}
    };}
    public void release(UUID nodeId){directory.releaseReservation(nodeId);}
}
