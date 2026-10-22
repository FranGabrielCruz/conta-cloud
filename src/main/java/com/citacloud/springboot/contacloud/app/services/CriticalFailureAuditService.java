package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.errors.ClassifiedError;
import com.citacloud.springboot.contacloud.app.errors.ErrorCategory;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CriticalFailureAuditService {
    private final JdbcTemplate jdbc;

    public CriticalFailureAuditService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void record(ClassifiedError error,String operation,String traceId) {
        if(!auditable(error.category())) return;
        var principal=TenantContext.principalActual();
        String detail="{\"errorCode\":\""+error.code().name()+"\",\"traceId\":\""+safe(traceId)
            +"\",\"operation\":\""+safe(operation)+"\"}";
        jdbc.update("""
            insert into auditoria(empresa_id,usuario_id,accion,entidad,detalle)
            values (?,?,?,'CRITICAL_OPERATION',cast(? as jsonb))
            """,principal.empresaId(),principal.usuarioId(),"CRITICAL_OPERATION_REJECTED",detail);
    }

    private static boolean auditable(ErrorCategory category){return category==ErrorCategory.AUTHORIZATION_ERROR
        ||category==ErrorCategory.IDEMPOTENCY_CONFLICT||category==ErrorCategory.CONCURRENCY_ERROR;}
    private static String safe(String value){if(value==null)return "";return value.replace("\\","\\\\").replace("\"","\\\"").replace("\n"," ").replace("\r"," ");}
}
