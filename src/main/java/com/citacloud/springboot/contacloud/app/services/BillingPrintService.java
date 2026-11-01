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
    private final FacturaVentaRepository facturas;
    private final PrintAgentRoutingService routing;
    private final FacturaVentaPdfService pdf;
    private final AgenteImpresionRepository agentes;
    private final AuditoriaService audit;
    public BillingPrintService(JdbcTemplate jdbc,FacturaVentaRepository facturas,PrintAgentRoutingService routing,
            FacturaVentaPdfService pdf,AgenteImpresionRepository agentes,AuditoriaService audit){
        this.jdbc=jdbc;this.facturas=facturas;this.routing=routing;this.pdf=pdf;this.agentes=agentes;this.audit=audit;
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('facturas.pdf')")
    public boolean mostrarPdf(UUID facturaId){
        var f=factura(facturaId);var p=TenantContext.principalActual();UUID id=routing.resolver(f.getCajaId());
        return agentes.findByIdAndTenantIdAndEmpresaId(id,p.tenantId(),p.empresaId())
            .filter(a->a.getEstado()==EstadoAgenteImpresion.ACTIVE).orElseThrow(()->new ReglaNegocioException("El agente de impresión de esta caja no está disponible.")).isMostrarPdfAlImprimir();
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('facturas.pdf')")
    public SolicitudImpresionResultadoDto solicitar(UUID facturaId,UUID intento){
        if(intento==null)throw new ReglaNegocioException("La solicitud de impresión no es válida.");
        var p=TenantContext.principalActual();var f=factura(facturaId);
        var existente=existente(p.tenantId(),p.empresaId(),facturaId,intento);if(existente.isPresent())return existente.get();
        UUID agentId=routing.resolver(f.getCajaId());
        var a=agentes.bloquear(agentId,p.tenantId(),p.empresaId()).filter(x->x.getEstado()==EstadoAgenteImpresion.ACTIVE&&x.getSucursalId().equals(f.getSucursalId()))
            .orElseThrow(()->new ReglaNegocioException("El agente de impresión de esta caja no está disponible."));
        if(a.getCajas().stream().noneMatch(c->c.getId().equals(f.getCajaId())))throw new ReglaNegocioException("La asignación de impresión cambió. Intenta nuevamente.");
        // Resolve output under the agent lock before generating or enqueueing a POS ticket.
        // A missing heartbeat must not prevent normal PDF viewing.
        if(a.isMostrarPdfAlImprimir())return new SolicitudImpresionResultadoDto(null,true);
        byte[] documento=pdf.generarParaImpresion(facturaId).contenido();
        if(documento.length>8_000_000)throw new ReglaNegocioException("El documento excede el tamaño permitido para impresión.");
        UUID id=UUID.randomUUID();
        int creados=jdbc.update("insert into billing_print_jobs(id,tenant_id,empresa_id,branch_id,cash_register_id,agent_id,document_id,idempotency_key,document_pdf,show_pdf_on_print) values(?,?,?,?,?,?,?,?,?,?) on conflict(tenant_id,empresa_id,idempotency_key) do nothing",
            id,p.tenantId(),p.empresaId(),f.getSucursalId(),f.getCajaId(),agentId,facturaId,intento,documento,false);
        if(creados==1)audit.registrar("PrintJobCreated","PrintJob",id,detalle(p.tenantId(),f.getSucursalId(),f.getCajaId(),agentId));
        return existente(p.tenantId(),p.empresaId(),facturaId,intento).orElseThrow(()->new ReglaNegocioException("El intento de impresión pertenece a otro documento."));
    }

    @Transactional @PreAuthorize("hasAuthority('PRINT_AGENT')")
    public Optional<TrabajoImpresionDto> siguiente(PrintAgentPrincipal p){
        var a=activo(p);if(a.getCajas().isEmpty())return Optional.empty();
        var jobs=jdbc.query("""
            select j.id from billing_print_jobs j
            where j.tenant_id=? and j.empresa_id=? and j.branch_id=? and j.agent_id=? and j.status='PENDING'
              and exists(select 1 from print_agent_cash_registers ar
                join cajas c on c.tenant_id=ar.tenant_id and c.empresa_id=ar.empresa_id and c.sucursal_id=ar.sucursal_id and c.id=ar.cash_register_id
                where ar.tenant_id=j.tenant_id and ar.empresa_id=j.empresa_id and ar.sucursal_id=j.branch_id
                  and ar.agent_id=j.agent_id and ar.cash_register_id=j.cash_register_id and ar.agent_status='ACTIVE' and c.activo=true)
            order by j.created_at,j.id limit 1 for update of j skip locked
            """,(r,n)->r.getObject(1,UUID.class),p.tenantId(),p.companyId(),a.getSucursalId(),a.getId());
        if(jobs.isEmpty())return Optional.empty();
        UUID id=jobs.getFirst(),claim=UUID.randomUUID();
        int count=jdbc.update("update billing_print_jobs set status='PROCESSING',claim_id=?,claimed_at=current_timestamp where id=? and tenant_id=? and empresa_id=? and agent_id=? and status='PENDING'",claim,id,p.tenantId(),p.companyId(),a.getId());
        if(count!=1)throw new RecursoNoEncontradoException("Trabajo no encontrado.");
        byte[] bytes=jdbc.queryForObject("select document_pdf from billing_print_jobs where id=? and tenant_id=? and empresa_id=? and agent_id=? and claim_id=?",byte[].class,id,p.tenantId(),p.companyId(),a.getId(),claim);
        return Optional.of(new TrabajoImpresionDto(id,claim,bytes));
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
        if(!"PROCESSING".equals(trabajo.estado()))throw new ReglaNegocioException("El trabajo no está reservado para impresión.");
        jdbc.update("update billing_print_jobs set status=? where id=? and claim_id=? and tenant_id=? and empresa_id=? and agent_id=? and status='PROCESSING'",estado,id,claim,p.tenantId(),p.companyId(),p.agentId());
        audit.registrarPara(p.companyId(),null,printed?"PrintJobPrinted":"PrintJobFailed","PrintJob",id,detalle(p.tenantId(),trabajo.sucursalId(),trabajo.cajaId(),p.agentId()));
    }

    private FacturaVenta factura(UUID id){var p=TenantContext.principalActual();return facturas.findByIdAndTenantIdAndEmpresaId(id,p.tenantId(),p.empresaId())
        .filter(x->EmpresaContext.permiteSucursal(x.getSucursalId())).orElseThrow(()->new RecursoNoEncontradoException("Factura no encontrada."));}
    private Optional<SolicitudImpresionResultadoDto> existente(UUID tenant,UUID empresa,UUID factura,UUID intento){return jdbc.query("select id,show_pdf_on_print from billing_print_jobs where tenant_id=? and empresa_id=? and idempotency_key=? and document_id=?",
        (r,n)->new SolicitudImpresionResultadoDto(r.getObject(1,UUID.class),false),tenant,empresa,intento,factura).stream().findFirst();}
    private AgenteImpresion activo(PrintAgentPrincipal p){return agentes.bloquear(p.agentId(),p.tenantId(),p.companyId()).filter(a->a.getEstado()==EstadoAgenteImpresion.ACTIVE).orElseThrow(VinculacionAgenteException::new);}
    private static String detalle(UUID tenant,UUID branch,UUID caja,UUID agent){return "{\"traceId\":\""+TraceContext.currentOrCreate()+"\",\"tenantId\":\""+tenant+"\",\"branchId\":\""+branch+"\",\"cashRegisterId\":\""+caja+"\",\"agentId\":\""+agent+"\"}";}
    private record TrabajoReservado(UUID sucursalId,UUID cajaId,String estado){}
}
