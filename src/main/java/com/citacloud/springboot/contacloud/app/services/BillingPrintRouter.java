package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.AgenteImpresionRepository;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import com.citacloud.springboot.contacloud.app.errors.TraceContext;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Shared billing output policy. Document adapters supply only an already authorized, stored origin.
 * New billing documents must use this router instead of creating print jobs themselves. */
@Service
public class BillingPrintRouter {
    private final JdbcTemplate jdbc;
    private final PrintAgentRoutingService routing;
    private final AgenteImpresionRepository agentes;
    private final AuditoriaService auditoria;
    public BillingPrintRouter(JdbcTemplate jdbc,PrintAgentRoutingService routing,AgenteImpresionRepository agentes,AuditoriaService auditoria){
        this.jdbc=jdbc;this.routing=routing;this.agentes=agentes;this.auditoria=auditoria;
    }
    record Documento(TipoDocumentoImpresion tipo,UUID id,UUID sucursalId,UUID cajaId,Supplier<ArchivoPdfDto> ticket,Supplier<Object> snapshot){
        Documento(TipoDocumentoImpresion tipo,UUID id,UUID sucursalId,UUID cajaId,Supplier<ArchivoPdfDto> ticket){this(tipo,id,sucursalId,cajaId,ticket,null);}
        Documento {Objects.requireNonNull(tipo);Objects.requireNonNull(id);Objects.requireNonNull(sucursalId);Objects.requireNonNull(cajaId);Objects.requireNonNull(ticket);}
    }
    // Internal entry point: only authorized document adapters in this package can construct Documento.
    @Transactional(propagation=Propagation.MANDATORY)
    SolicitudImpresionResultadoDto solicitar(Documento d,UUID intento){
        if(intento==null)throw new ReglaNegocioException("La solicitud de impresión no es válida.");
        var p=TenantContext.principalActual();
        var previa=existente(d,intento);if(previa.isPresent())return previa.get();
        var priorOutput=beginRequest(d,intento);if(priorOutput!=null)return priorOutput;
        UUID agentId=routing.resolver(d.cajaId());
        var a=agentes.bloquear(agentId,p.tenantId(),p.empresaId())
            .filter(x->d.sucursalId().equals(x.getSucursalId()))
            .orElseThrow(()->new ReglaNegocioException("El agente de impresión de esta caja no está disponible."));
        if(a.getCajas().stream().noneMatch(c->c.getId().equals(d.cajaId())))
            throw new ReglaNegocioException("La asignación de impresión cambió. Intenta nuevamente.");
        // Recheck after the lock: an overlapping request may already have committed the same job.
        previa=existente(d,intento);if(previa.isPresent())return previa.get();
        if(a.isPrintOnScreen()){
            finishRequest(intento,agentId,null,"SCREEN");
            return new SolicitudImpresionResultadoDto(null,true);
        }
        if(a.getEstado()!=EstadoAgenteImpresion.ACTIVE)
            throw new ReglaNegocioException("El agente de impresión está revocado o no está disponible. Revisa su vinculación.");
        byte[] pdf=d.ticket().get().contenido();
        if(pdf==null||pdf.length==0||pdf.length>8_000_000)
            throw new ReglaNegocioException("El documento excede los límites permitidos para impresión.");
        String snapshot=null;
        if(d.snapshot()!=null){
            Object document=d.snapshot().get();
            if(document==null)throw new ReglaNegocioException("El documento no conserva un snapshot imprimible.");
            snapshot=tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(document);
            if(snapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>2_000_000)throw new ReglaNegocioException("El snapshot excede los límites permitidos para impresión.");
        }
        UUID id=UUID.randomUUID();
        int count=jdbc.update("insert into billing_print_jobs(id,tenant_id,empresa_id,branch_id,cash_register_id,agent_id,document_id,idempotency_key,document_pdf,print_on_screen,document_type) values(?,?,?,?,?,?,?,?,?,?,?) on conflict(tenant_id,empresa_id,idempotency_key) do nothing",
            id,p.tenantId(),p.empresaId(),d.sucursalId(),d.cajaId(),agentId,d.id(),intento,pdf,false,d.tipo().name());
        if(count==1&&snapshot!=null)jdbc.update("update billing_print_jobs set snapshot_json=?,snapshot_version=1,trace_id=? where id=? and tenant_id=? and empresa_id=? and agent_id=?",snapshot,TraceContext.currentOrCreate(),id,p.tenantId(),p.empresaId(),agentId);
        if(count==1)auditoria.registrar("PrintJobCreated","PrintJob",id,
            "{\"documentType\":\"%s\",\"documentId\":\"%s\",\"tenantId\":\"%s\",\"companyId\":\"%s\",\"branchId\":\"%s\",\"cashRegisterId\":\"%s\",\"agentId\":\"%s\",\"traceId\":\"%s\"}"
                .formatted(d.tipo(),d.id(),p.tenantId(),p.empresaId(),d.sucursalId(),d.cajaId(),agentId,TraceContext.currentOrCreate()));
        var output=existente(d,intento).orElseThrow(()->new ReglaNegocioException("El intento de impresión pertenece a otro documento."));
        finishRequest(intento,agentId,output.trabajoId(),"PRINT_AGENT");
        return output;
    }
    private SolicitudImpresionResultadoDto beginRequest(Documento d,UUID attempt){
        var p=TenantContext.principalActual();
        jdbc.update("insert into billing_print_requests(tenant_id,empresa_id,idempotency_key,document_type,document_id) values(?,?,?,?,?) on conflict(tenant_id,empresa_id,idempotency_key) do nothing",p.tenantId(),p.empresaId(),attempt,d.tipo().name(),d.id());
        return jdbc.query("select document_type,document_id,output_mode,job_id from billing_print_requests where tenant_id=? and empresa_id=? and idempotency_key=? for update",rs->{
            if(!rs.next())throw new ReglaNegocioException("No fue posible reservar el intento de impresión.");
            if(!d.tipo().name().equals(rs.getString(1))||!d.id().equals(rs.getObject(2,UUID.class)))throw new ReglaNegocioException("El intento de impresión pertenece a otro documento.");
            String mode=rs.getString(3);if(mode==null)return null;
            return new SolicitudImpresionResultadoDto(rs.getObject(4,UUID.class),"SCREEN".equals(mode));
        },p.tenantId(),p.empresaId(),attempt);
    }
    private void finishRequest(UUID attempt,UUID agent,UUID job,String mode){
        var p=TenantContext.principalActual();
        jdbc.update("update billing_print_requests set output_mode=?,agent_id=?,job_id=? where tenant_id=? and empresa_id=? and idempotency_key=? and output_mode is null",mode,agent,job,p.tenantId(),p.empresaId(),attempt);
    }
    private Optional<SolicitudImpresionResultadoDto> existente(Documento d,UUID intento){
        var p=TenantContext.principalActual();
        // Enum is server-defined. A historical print job always remains a physical output on retry.
        return jdbc.query("select id,print_on_screen from billing_print_jobs where tenant_id=? and empresa_id=? and idempotency_key=? and document_id=? and document_type='"+d.tipo().name()+"'",
            (r,n)->new SolicitudImpresionResultadoDto(r.getObject(1,UUID.class),false),p.tenantId(),p.empresaId(),intento,d.id()).stream().findFirst();
    }
}
