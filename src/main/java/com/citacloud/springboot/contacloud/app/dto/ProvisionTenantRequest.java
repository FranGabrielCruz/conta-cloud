package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.multitenancy.HostingMode;import java.util.*;
public record ProvisionTenantRequest(String idempotencyKey,NuevaEmpresaDto empresa,Set<String> moduleKeys,HostingMode hostingMode,UUID databaseNodeId){}
