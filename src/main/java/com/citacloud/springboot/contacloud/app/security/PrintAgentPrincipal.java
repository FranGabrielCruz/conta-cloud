package com.citacloud.springboot.contacloud.app.security;

import java.util.UUID;
public record PrintAgentPrincipal(UUID agentId,UUID tenantId,UUID companyId,UUID branchId,String displayCode,String name){public String tokenType(){return "PrintAgent";}}
