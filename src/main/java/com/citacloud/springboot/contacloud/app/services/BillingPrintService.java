package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.security.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.errors.TraceContext;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;

@Service
public class BillingPrintService {
    private final JdbcTemplate jdbc;
    private PrintJobRetryPolicy queuePolicy=PrintJobRetryPolicy.defaults();
    @org.springframework.beans.factory.annotation.Autowired public void configureQueuePolicy(PrintJobRetryPolicy policy){this.queuePolicy=policy;}
    private final FacturaVentaRepository facturas;
    private final PrintAgentRoutingService routing;
    private final FacturaVentaPdfService pdf;
    private final AgenteImpresionRepository agentes;
    private final AuditoriaService audit;
    private final BillingPrintRouter router;
    public BillingPrintService(JdbcTemplate jdbc,FacturaVentaRepository facturas,PrintAgentRoutingService routing,
            FacturaVentaPdfService pdf,AgenteImpresionRepository agentes,AuditoriaService audit){
        this(jdbc,facturas,routing,pdf,agentes,audit,new BillingPrintRouter(jdbc,routing,agentes,audit));
    }
    @org.springframework.beans.factory.annotation.Autowired
    public BillingPrintService(JdbcTemplate jdbc,FacturaVentaRepository facturas,PrintAgentRoutingService routing,
            FacturaVentaPdfService pdf,AgenteImpresionRepository agentes,AuditoriaService audit,BillingPrintRouter router){
        this.jdbc=jdbc;this.facturas=facturas;this.routing=routing;this.pdf=pdf;this.agentes=agentes;this.audit=audit;this.router=router;
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('facturas.pdf')")
    public boolean printOnScreen(UUID facturaId){
        var f=factura(facturaId);var p=TenantContext.principalActual();UUID id=routing.resolver(f.getCajaId());
        return agentes.findByIdAndTenantIdAndEmpresaId(id,p.tenantId(),p.empresaId())
            .filter(a->a.getEstado()==EstadoAgenteImpresion.ACTIVE||a.isPrintOnScreen()).orElseThrow(()->new ReglaNegocioException("El agente de impresión de esta caja no está disponible.")).isPrintOnScreen();
    }

    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('facturas.pdf')")
    public SolicitudImpresionResultadoDto solicitar(UUID facturaId,UUID intento){
        var f=factura(facturaId);
        return router.solicitar(new BillingPrintRouter.Documento(TipoDocumentoImpresion.SALES_INVOICE,facturaId,
            f.getSucursalId(),f.getCajaId(),()->pdf.generarParaImpresion(facturaId),()->pdf.documentoParaImpresion(facturaId)),intento);
    }

    @Transactional @PreAuthorize("hasAuthority('PRINT_AGENT')")
    public Optional<TrabajoImpresionDto> siguiente(PrintAgentPrincipal p){return siguiente(p,true);}
    @Transactional @PreAuthorize("hasAuthority('PRINT_AGENT')")
    public Optional<TrabajoImpresionDto> siguiente(PrintAgentPrincipal p,boolean reliable){
        var a=activo(p);recover(p,a.getSucursalId());
        if(a.getCajas().isEmpty())return Optional.empty();
        var jobs=jdbc.query("""
            select j.id from billing_print_jobs j
            where j.tenant_id=? and j.empresa_id=? and j.branch_id=? and j.agent_id=? and j.status='PENDING' and j.available_at<=current_timestamp and j.attempt_count<%d
              and exists(select 1 from print_agent_cash_registers ar
                join cajas c on c.tenant_id=ar.tenant_id and c.empresa_id=ar.empresa_id and c.sucursal_id=ar.sucursal_id and c.id=ar.cash_register_id
                where ar.tenant_id=j.tenant_id and ar.empresa_id=j.empresa_id and ar.sucursal_id=j.branch_id
                  and ar.agent_id=j.agent_id and ar.cash_register_id=j.cash_register_id and ar.agent_status='ACTIVE' and c.activo=true)
            order by j.available_at,j.created_at,j.id limit 1 for update of j skip locked
            """.formatted(queuePolicy.maximumAttempts),(r,n)->r.getObject(1,UUID.class),p.tenantId(),p.companyId(),a.getSucursalId(),a.getId());
        if(jobs.isEmpty())return Optional.empty();
        UUID id=jobs.getFirst(),claim=UUID.randomUUID();
        int count=jdbc.update("update billing_print_jobs set status='PROCESSING',claim_id=?,claimed_at=current_timestamp,last_attempt_at=current_timestamp,attempt_count=attempt_count+1,output_started_at=null,protocol_version="+(reliable?2:1)+",lease_until=current_timestamp+ interval '"+queuePolicy.leaseSeconds+"' second where id=? and tenant_id=? and empresa_id=? and agent_id=? and status='PENDING'",claim,id,p.tenantId(),p.companyId(),a.getId());
        if(count!=1)throw new RecursoNoEncontradoException("Trabajo no encontrado.");
        byte[] bytes=jdbc.queryForObject("select document_pdf from billing_print_jobs where id=? and tenant_id=? and empresa_id=? and agent_id=? and claim_id=?",byte[].class,id,p.tenantId(),p.companyId(),a.getId(),claim);
        String type=jdbc.queryForObject("select document_type from billing_print_jobs where id=? and tenant_id=? and empresa_id=? and agent_id=? and claim_id=?",String.class,id,p.tenantId(),p.companyId(),a.getId(),claim);
        String snapshot=jdbc.queryForObject("select snapshot_json from billing_print_jobs where id=? and tenant_id=? and empresa_id=? and agent_id=? and claim_id=?",String.class,id,p.tenantId(),p.companyId(),a.getId(),claim);
        Integer version=jdbc.queryForObject("select snapshot_version from billing_print_jobs where id=? and tenant_id=? and empresa_id=? and agent_id=? and claim_id=?",Integer.class,id,p.tenantId(),p.companyId(),a.getId(),claim);
        String trace=jdbc.queryForObject("select trace_id from billing_print_jobs where id=? and tenant_id=? and empresa_id=? and agent_id=? and claim_id=?",String.class,id,p.tenantId(),p.companyId(),a.getId(),claim);
        Integer attempt=jdbc.queryForObject("select attempt_count from billing_print_jobs where id=? and tenant_id=? and empresa_id=? and agent_id=? and claim_id=?",Integer.class,id,p.tenantId(),p.companyId(),a.getId(),claim);
        org.slf4j.LoggerFactory.getLogger(getClass()).info("event=PrintJobClaimed jobId={} agentId={} attempt={} traceId={}",id,a.getId(),attempt,trace);
        return Optional.of(new TrabajoImpresionDto(id,claim,bytes,type==null?TipoDocumentoImpresion.SALES_INVOICE:TipoDocumentoImpresion.valueOf(type),version,snapshot,trace,reliable?2:1,attempt,queuePolicy.leaseSeconds));
    }

    @Transactional @PreAuthorize("hasAuthority('PRINT_AGENT')")
    public void completar(PrintAgentPrincipal p,UUID id,UUID claim,boolean printed){
        activo(p);
        // A claimed job may finish after its register was removed. Its original agent and claim must still match.
        var autorizados=jdbc.query("select branch_id,cash_register_id,status from billing_print_jobs where id=? and claim_id=? and tenant_id=? and empresa_id=? and agent_id=? for update",
            (r,n)->new TrabajoReservado(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3)),id,claim,p.tenantId(),p.companyId(),p.agentId());
        if(autorizados.isEmpty())throw new RecursoNoEncontradoException("Trabajo no encontrado.");
        var trabajo=autorizados.getFirst();String estado=printed?"PRINTED":"FAILED";
        if(estado.equals(trabajo.estado()))return;
        boolean reconcile=printed&&"FAILED".equals(trabajo.estado())&&"OUTPUT_UNCERTAIN".equals(jdbc.queryForObject("select last_error_code from billing_print_jobs where id=? and claim_id=? and tenant_id=? and empresa_id=? and agent_id=?",String.class,id,claim,p.tenantId(),p.companyId(),p.agentId()));
        if(!"PROCESSING".equals(trabajo.estado())&&!reconcile)throw new ReglaNegocioException("El trabajo no está reservado para impresión.");
        if(reconcile){jdbc.update("update billing_print_jobs set status='PRINTED',last_error_code=null,completed_at=current_timestamp,lease_until=null where id=? and claim_id=? and tenant_id=? and empresa_id=? and agent_id=? and status='FAILED'",id,claim,p.tenantId(),p.companyId(),p.agentId());audit.registrarPara(p.companyId(),null,"PrintJobPrinted","PrintJob",id,detalle(p.tenantId(),trabajo.sucursalId(),trabajo.cajaId(),p.agentId()));return;}
        jdbc.update("update billing_print_jobs set status=?,completed_at=current_timestamp,lease_until=null where id=? and claim_id=? and tenant_id=? and empresa_id=? and agent_id=? and status='PROCESSING'",estado,id,claim,p.tenantId(),p.companyId(),p.agentId());
        audit.registrarPara(p.companyId(),null,printed?"PrintJobPrinted":"PrintJobFailed","PrintJob",id,detalle(p.tenantId(),trabajo.sucursalId(),trabajo.cajaId(),p.agentId()));
    }

    private void recover(PrintAgentPrincipal p,UUID branch){
        int uncertain=jdbc.update("update billing_print_jobs set status='FAILED',completed_at=current_timestamp,last_error_code='OUTPUT_UNCERTAIN',lease_until=null where tenant_id=? and empresa_id=? and branch_id=? and agent_id=? and status='PROCESSING' and lease_until<=current_timestamp and (output_started_at is not null or protocol_version<2)",p.tenantId(),p.companyId(),branch,p.agentId());
        int recovered=jdbc.update("update billing_print_jobs set status=case when attempt_count>=? then 'FAILED' else 'PENDING' end,completed_at=case when attempt_count>="+queuePolicy.maximumAttempts+" then current_timestamp else null end,available_at=current_timestamp,last_error_code='LEASE_EXPIRED',lease_until=null where tenant_id=? and empresa_id=? and branch_id=? and agent_id=? and status='PROCESSING' and lease_until<=current_timestamp and output_started_at is null and protocol_version=2",queuePolicy.maximumAttempts,p.tenantId(),p.companyId(),branch,p.agentId());
        if(recovered+uncertain>0)org.slf4j.LoggerFactory.getLogger(getClass()).info("event=PrintJobRecovered agentId={} retryable={} uncertain={}",p.agentId(),recovered,uncertain);
    }
    @Transactional @PreAuthorize("hasAuthority('PRINT_AGENT')")
    public void renovarReserva(PrintAgentPrincipal p,UUID id,UUID claim,boolean start){
        var a=activo(p);
        String assignment=start?" and exists(select 1 from print_agent_cash_registers ar join cajas c on c.tenant_id=ar.tenant_id and c.empresa_id=ar.empresa_id and c.id=ar.cash_register_id and c.sucursal_id=ar.sucursal_id where ar.tenant_id=j.tenant_id and ar.empresa_id=j.empresa_id and ar.sucursal_id=j.branch_id and ar.cash_register_id=j.cash_register_id and ar.agent_id=j.agent_id and ar.agent_status='ACTIVE' and c.activo=true)":"";
        int count=jdbc.update("update billing_print_jobs j set lease_until=current_timestamp+ interval '"+queuePolicy.leaseSeconds+"' second"+(start?",output_started_at=coalesce(output_started_at,current_timestamp)":"")+" where j.id=? and j.claim_id=? and j.tenant_id=? and j.empresa_id=? and j.agent_id=? and j.branch_id=? and j.status='PROCESSING' and j.lease_until>current_timestamp and j.protocol_version=2"+assignment,id,claim,p.tenantId(),p.companyId(),p.agentId(),a.getSucursalId());
        if(count!=1)throw new ReglaNegocioException("La reserva de impresión ya no está disponible.");
    }
    @Transactional @PreAuthorize("hasAuthority('PRINT_AGENT')")
    public void resultado(PrintAgentPrincipal p,UUID id,UUID claim,boolean printed,String errorCode){
        var a=activo(p);String code=PrintJobRetryPolicy.validateCode(printed,errorCode);
        var rows=jdbc.query("select status,attempt_count,last_result_claim_id,last_result_printed,last_error_code,output_started_at,protocol_version,trace_id from billing_print_jobs where id=? and claim_id=? and tenant_id=? and empresa_id=? and agent_id=? and branch_id=? for update",(r,n)->new QueueRow(r.getString(1),r.getInt(2),r.getObject(3,UUID.class),(Boolean)r.getObject(4),r.getString(5),r.getTimestamp(6)!=null,r.getInt(7),r.getString(8)),id,claim,p.tenantId(),p.companyId(),p.agentId(),a.getSucursalId());
        if(rows.isEmpty())throw new RecursoNoEncontradoException("Trabajo no encontrado.");var job=rows.getFirst();
        if(claim.equals(job.resultClaim())&&Boolean.valueOf(printed).equals(job.resultPrinted())&&Objects.equals(code,job.error()))return;
        boolean reconciliation=printed&&"FAILED".equals(job.status())&&"OUTPUT_UNCERTAIN".equals(job.error());
        if(!"PROCESSING".equals(job.status())&&!reconciliation)throw new ReglaNegocioException("El trabajo no permite esta transición.");
        if(printed&&job.protocol()==2&&!job.started())throw new ReglaNegocioException("El trabajo no inició su salida autorizada.");
        boolean retry=!printed&&queuePolicy.transientError(code)&&job.attempt()<queuePolicy.maximumAttempts;
        String status=printed?"PRINTED":retry?"PENDING":"FAILED";int delay=retry?queuePolicy.delaySeconds(job.attempt()):0;
        jdbc.update("update billing_print_jobs set status=?,last_error_code=?,last_result_claim_id=claim_id,last_result_printed=?,lease_until=null,available_at=current_timestamp+ interval '"+delay+"' second,completed_at="+(retry?"null":"current_timestamp")+" where id=? and claim_id=? and tenant_id=? and empresa_id=? and agent_id=?",status,code,printed,id,claim,p.tenantId(),p.companyId(),p.agentId());
        org.slf4j.LoggerFactory.getLogger(getClass()).info("event={} jobId={} agentId={} attempt={} status={} errorCode={} traceId={}",retry?"PrintJobRetryScheduled":printed?"PrintJobCompleted":"PrintJobFailed",id,p.agentId(),job.attempt(),status,code,job.trace());
        audit.registrarPara(p.companyId(),null,retry?"PrintJobRetryScheduled":printed?"PrintJobPrinted":"PrintJobFailed","PrintJob",id,"{\"traceId\":\""+(job.trace()==null?TraceContext.currentOrCreate():job.trace())+"\",\"attempt\":"+job.attempt()+",\"status\":\""+status+"\"}");
    }
    private record QueueRow(String status,int attempt,UUID resultClaim,Boolean resultPrinted,String error,boolean started,int protocol,String trace){}

    private FacturaVenta factura(UUID id){var p=TenantContext.principalActual();return facturas.findByIdAndTenantIdAndEmpresaId(id,p.tenantId(),p.empresaId())
        .filter(x->EmpresaContext.permiteSucursal(x.getSucursalId())).orElseThrow(()->new RecursoNoEncontradoException("Factura no encontrada."));}
    private AgenteImpresion activo(PrintAgentPrincipal p){return agentes.bloquear(p.agentId(),p.tenantId(),p.companyId()).filter(a->a.getEstado()==EstadoAgenteImpresion.ACTIVE).orElseThrow(VinculacionAgenteException::new);}
    private static String detalle(UUID tenant,UUID branch,UUID caja,UUID agent){return "{\"traceId\":\""+TraceContext.currentOrCreate()+"\",\"tenantId\":\""+tenant+"\",\"branchId\":\""+branch+"\",\"cashRegisterId\":\""+caja+"\",\"agentId\":\""+agent+"\"}";}
    private record TrabajoReservado(UUID sucursalId,UUID cajaId,String estado){}
}
