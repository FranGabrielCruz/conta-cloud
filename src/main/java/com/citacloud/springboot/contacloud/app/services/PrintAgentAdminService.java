package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.AgenteImpresionMapper;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import com.citacloud.springboot.contacloud.app.errors.TraceContext;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrintAgentAdminService {
    private final CashRegisterRepository cajas;
    private final AgenteImpresionRepository agentes;
    private final VinculacionAgenteImpresionRepository pairings;
    private final CredencialAgenteImpresionRepository credenciales;
    private final SucursalRepository sucursales;
    private final AgenteImpresionMapper mapper;
    private final PrintAgentNumberService numeros;
    private final PrintAgentTokenService tokens;
    private final AuditoriaService auditoria;
    private final PrintAgentAssignmentService asignaciones;
    private final JdbcTemplate jdbc;
    private final Duration vidaCodigo;
    private final Duration toleranciaConexion;

    public PrintAgentAdminService(CashRegisterRepository cajas,AgenteImpresionRepository agentes,
            VinculacionAgenteImpresionRepository pairings,CredencialAgenteImpresionRepository credenciales,
            SucursalRepository sucursales,AgenteImpresionMapper mapper,PrintAgentNumberService numeros,
            PrintAgentTokenService tokens,AuditoriaService auditoria,PrintAgentAssignmentService asignaciones,JdbcTemplate jdbc,
            @Value("${contacloud.print-agents.pairing-ttl:PT10M}") Duration vidaCodigo,
            @Value("${contacloud.print-agents.connected-tolerance:PT2M}") Duration toleranciaConexion){
        this.cajas=cajas;this.agentes=agentes;this.pairings=pairings;this.credenciales=credenciales;
        this.sucursales=sucursales;this.mapper=mapper;this.numeros=numeros;this.tokens=tokens;
        this.auditoria=auditoria;this.asignaciones=asignaciones;this.jdbc=jdbc;
        this.vidaCodigo=vidaCodigo;this.toleranciaConexion=toleranciaConexion;
    }

    @Transactional(readOnly=true) @PreAuthorize("hasAuthority('agentes_impresion.ver')")
    public Page<AgenteImpresionDto> buscar(String buscar,EstadoAgenteImpresion estado,int pagina,int tamano){
        var p=TenantContext.principalActual();
        Set<UUID> permitidas=p.sucursalIds().isEmpty()?Set.of(new UUID(0,0)):p.sucursalIds();
        var page=agentes.buscar(p.tenantId(),p.empresaId(),buscar==null?"":buscar.trim(),estado,
            p.accesoTodasSucursales(),permitidas,PageRequest.of(Math.max(0,pagina),permitido(tamano),Sort.by(Sort.Order.desc("creadoEn"),Sort.Order.desc("id"))));
        if(page.isEmpty())return page.map(a->null);
        var branches=sucursales.findAllByIdInAndEmpresaId(page.stream().map(AgenteImpresion::getSucursalId).collect(Collectors.toSet()),p.empresaId())
            .stream().collect(Collectors.toMap(Sucursal::getId,Sucursal::getNombre));
        var asignadas=consultarCajas(page.stream().map(AgenteImpresion::getId).toList());
        var now=OffsetDateTime.now();
        return page.map(a->mapper.toDto(a,branches.getOrDefault(a.getSucursalId(),"—"),asignadas.getOrDefault(a.getId(),List.of()),toleranciaConexion,now));
    }

    @Transactional(readOnly=true) @PreAuthorize("hasAuthority('agentes_impresion.ver')")
    public AgenteImpresionDto obtener(UUID id){return dto(agente(id,false));}

    @Transactional @PreAuthorize("hasAuthority('agentes_impresion.crear')")
    public AgenteImpresionDto crear(CrearAgenteImpresionInput input){
        if(input==null)throw new ReglaNegocioException("Completa los datos del agente.");
        validarNombre(input.nombre());validarSucursal(input.sucursalId());validarPreferencia(input.mostrarPdfAlImprimir(),true);
        var p=TenantContext.principalActual();
        var seleccion=asignaciones.validar(p.tenantId(),p.empresaId(),input.sucursalId(),null,input.cajaIds(),List.of(),true);
        var a=mapper.toEntity(input,p.tenantId(),p.empresaId(),numeros.siguiente(p.tenantId(),p.empresaId()),p.usuarioId(),seleccion);
        agentes.saveAndFlush(a);auditoria.registrar("AgentCreated","PrintAgent",a.getId(),detalle(a));return dto(a);
    }

    @Transactional @PreAuthorize("hasAuthority('agentes_impresion.editar')")
    public AgenteImpresionDto editar(UUID id,EditarAgenteImpresionInput input){
        if(input==null)throw new ReglaNegocioException("Completa los datos del agente.");
        var a=agente(id,true);
        if(a.getVersion()!=input.version())throw new ReglaNegocioException("El agente cambió. Vuelve a abrir el formulario antes de guardar.");
        validarNombre(input.nombre());validarSucursal(input.sucursalId());validarPreferencia(input.mostrarPdfAlImprimir(),a.isMostrarPdfAlImprimir());
        var anteriores=a.getCajas().stream().map(Caja::getId).toList();
        var p=TenantContext.principalActual();
        var seleccion=asignaciones.validar(p.tenantId(),p.empresaId(),input.sucursalId(),id,input.cajaIds(),anteriores,a.getEstado()!=EstadoAgenteImpresion.REVOKED);
        // Remove old join rows before a branch change; composite foreign keys remain valid at each flush.
        a.reemplazarCajas(Set.of());agentes.flush();
        a.configurar(input.nombre().trim(),input.sucursalId(),input.mostrarPdfAlImprimir());a.reemplazarCajas(seleccion);agentes.flush();
        var retiradas=new HashSet<>(anteriores);retiradas.removeAll(input.cajaIds());cancelarPendientes(a,retiradas);
        auditoria.registrar("PrintAgentConfigurationChanged","PrintAgent",id,detalle(a));return dto(a);
    }

    @Transactional @PreAuthorize("hasAuthority('agentes_impresion.codigo')")
    public CodigoVinculacionDto generarCodigo(UUID id){
        var p=TenantContext.principalActual();var a=agente(id,true);a.prepararRevinculacion();
        var now=OffsetDateTime.now();pairings.invalidarActivas(p.tenantId(),p.empresaId(),id,now);
        String code=nuevoCodigo();var pairing=pairings.saveAndFlush(new VinculacionAgenteImpresion(p.tenantId(),p.empresaId(),id,tokens.hashCodigo(code),now.plus(vidaCodigo),p.usuarioId()));
        auditoria.registrar("PairingCodeGenerated","PrintAgent",id,"{\"pairingId\":\""+pairing.getId()+"\"}");
        return new CodigoVinculacionDto(id,code,pairing.getExpiraEn());
    }

    @Transactional @PreAuthorize("hasAuthority('agentes_impresion.revocar')")
    public AgenteImpresionDto revocar(UUID id){
        var p=TenantContext.principalActual();var a=agente(id,true);
        if(a.getEstado()!=EstadoAgenteImpresion.REVOKED){var now=OffsetDateTime.now();a.revocar(p.usuarioId());
            pairings.invalidarActivas(p.tenantId(),p.empresaId(),id,now);credenciales.revocarActivas(p.tenantId(),p.empresaId(),id,now);
            agentes.saveAndFlush(a);auditoria.registrar("AgentRevoked","PrintAgent",id,detalle(a));}
        return dto(a);
    }

    @Transactional(readOnly=true) @PreAuthorize("hasAnyAuthority('agentes_impresion.crear','agentes_impresion.editar')")
    public List<OpcionSucursal> sucursales(){return sucursales.findAllByEmpresaIdAndActivoTrueOrderByNombre(TenantContext.requerirEmpresaId()).stream()
        .filter(s->EmpresaContext.permiteSucursal(s.getId())).map(s->new OpcionSucursal(s.getId(),s.getNombre())).toList();}

    @Transactional(readOnly=true) @PreAuthorize("hasAnyAuthority('agentes_impresion.crear','agentes_impresion.editar')")
    public List<CajaAgenteImpresionDto> cajas(UUID sucursal){
        var p=TenantContext.principalActual();if(sucursal==null||!EmpresaContext.permiteSucursal(sucursal))return List.of();
        return cajas.findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(p.tenantId(),p.empresaId()).stream()
            .filter(c->c.getSucursalId().equals(sucursal)).map(c->new CajaAgenteImpresionDto(c.getId(),c.getNombre())).toList();
    }

    private AgenteImpresion agente(UUID id,boolean bloquear){
        var p=TenantContext.principalActual();var a=(bloquear?agentes.bloquear(id,p.tenantId(),p.empresaId()):agentes.findByIdAndTenantIdAndEmpresaId(id,p.tenantId(),p.empresaId()))
            .filter(x->EmpresaContext.permiteSucursal(x.getSucursalId())).orElseThrow(()->new RecursoNoEncontradoException("No se encontró el agente de impresión."));
        return a;
    }
    private AgenteImpresionDto dto(AgenteImpresion a){String sucursal=sucursales.findByIdAndEmpresaId(a.getSucursalId(),a.getEmpresaId()).map(Sucursal::getNombre).orElse("—");
        return mapper.toDto(a,sucursal,consultarCajas(List.of(a.getId())).getOrDefault(a.getId(),List.of()),toleranciaConexion,OffsetDateTime.now());}
    private Map<UUID,List<CajaAgenteImpresionDto>> consultarCajas(List<UUID> ids){
        var p=TenantContext.principalActual();Map<UUID,List<CajaAgenteImpresionDto>> resultado=new HashMap<>();
        for(var c:agentes.cajasAsignadas(p.tenantId(),p.empresaId(),ids))resultado.computeIfAbsent(c.getAgenteId(),i->new ArrayList<>()).add(new CajaAgenteImpresionDto(c.getCajaId(),c.getNombre()));
        return resultado;
    }
    private void validarSucursal(UUID id){if(id==null||!EmpresaContext.permiteSucursal(id)||sucursales.findByIdAndEmpresaId(id,TenantContext.requerirEmpresaId()).filter(Sucursal::isActivo).isEmpty())throw new ReglaNegocioException("Selecciona una sucursal activa y autorizada.");}
    private void validarNombre(String nombre){if(nombre==null||nombre.isBlank()||nombre.trim().length()>150)throw new ReglaNegocioException("Indica un nombre de agente válido.");}
    private void validarPreferencia(boolean valor,boolean anterior){if(valor!=anterior&&!TenantContext.principalActual().permisos().contains("facturas.configurar_impresion"))throw new AccessDeniedException("No tienes permiso para cambiar la visualización automática del PDF.");}
    private void cancelarPendientes(AgenteImpresion a,Set<UUID> retiradas){
        if(retiradas.isEmpty())return;List<Object> args=new ArrayList<>(List.of(a.getTenantId(),a.getEmpresaId(),a.getId()));args.addAll(retiradas);
        jdbc.update("update billing_print_jobs set status='CANCELLED' where tenant_id=? and empresa_id=? and agent_id=? and status='PENDING' and cash_register_id in ("+String.join(",",Collections.nCopies(retiradas.size(),"?"))+")",args.toArray());
    }
    private String nuevoCodigo(){for(int i=0;i<100;i++){String code=tokens.nuevoCodigoVinculacion();if(!pairings.existsByHashCodigo(tokens.hashCodigo(code)))return code;}throw new ReglaNegocioException("No fue posible generar el código. Intenta nuevamente.");}
    private static int permitido(int size){return Set.of(10,20,25,50,100).contains(size)?size:10;}
    private static String detalle(AgenteImpresion a){return "{\"traceId\":\""+TraceContext.currentOrCreate()+"\",\"showPdfOnPrint\":"+a.isMostrarPdfAlImprimir()+",\"cajas\":"+a.getCajas().size()+"}";}
    public record OpcionSucursal(UUID id,String nombre){}
}
