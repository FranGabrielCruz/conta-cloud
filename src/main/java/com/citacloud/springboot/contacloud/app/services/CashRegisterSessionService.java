package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.SesionCajaMapper;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CashRegisterSessionService {
    private static final Set<Integer> PAGE_SIZES=Set.of(10,25,50,100);
    private static final UUID EMPTY_ID=new UUID(0L,0L);
    private final CashRegisterSessionRepository sessions;
    private final CashRegisterRepository cajas;
    private final FinancialMovementRepository movimientos;
    private final EmpresaRepository empresas;
    private final SucursalRepository sucursales;
    private final SesionCajaMapper mapper;
    private final AuditoriaService auditoria;

    public CashRegisterSessionService(CashRegisterSessionRepository sessions,CashRegisterRepository cajas,
            FinancialMovementRepository movimientos,EmpresaRepository empresas,SesionCajaMapper mapper,
            AuditoriaService auditoria,SucursalRepository sucursales){
        this.sessions=sessions;this.cajas=cajas;this.movimientos=movimientos;this.empresas=empresas;
        this.mapper=mapper;this.auditoria=auditoria;this.sucursales=sucursales;
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('operaciones_caja.ver')")
    public List<CajaCatalogosDto.SucursalOpcion> sucursalesOperables(){
        var p=TenantContext.principalActual();UUID empresa=EmpresaContext.requerirEmpresaId();
        List<Sucursal> lista=p.accesoTodasSucursales()
            ?sucursales.findAllByEmpresaIdAndActivoTrueOrderByNombre(empresa)
            :sucursales.findAllByIdInAndEmpresaId(p.sucursalIds(),empresa).stream().filter(Sucursal::isActivo).toList();
        return lista.stream().sorted(Comparator.comparing(Sucursal::getNombre))
            .map(s->new CajaCatalogosDto.SucursalOpcion(s.getId(),s.getNombre())).toList();
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('operaciones_caja.ver')")
    public LocalDate fechaOperativaActual(){var p=TenantContext.principalActual();return LocalDate.now(zonaEmpresa(
        EmpresaContext.requerirEmpresaId(),p.tenantId()));}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('operaciones_caja.ver')")
    public Page<OperacionCajaDto> buscarOperaciones(String buscar,UUID sucursalId,Boolean abierta,
            int pagina,int tamano,String ordenarPor,boolean ascendente){
        validarPagina(pagina,tamano);
        if(sucursalId!=null&&!EmpresaContext.permiteSucursal(sucursalId))
            throw new RecursoNoEncontradoException("Sucursal no encontrada.");
        var p=TenantContext.principalActual();UUID empresa=EmpresaContext.requerirEmpresaId();
        Set<UUID> sucursales=sucursalesPermitidas(p.sucursalIds());
        Page<Caja> page=cajas.buscarOperaciones(p.tenantId(),empresa,limpiar(buscar),sucursalId,abierta,
            p.accesoTodasSucursales(),sucursales,PageRequest.of(pagina,tamano,ordenCaja(ordenarPor,ascendente)));
        Map<UUID,SesionCaja> abiertas=sessions.findAllByTenantIdAndEmpresaIdAndCajaIdInAndStatus(
            p.tenantId(),empresa,page.getContent().stream().map(Caja::getId).toList(),EstadoSesionCaja.OPEN)
            .stream().collect(Collectors.toMap(SesionCaja::getCajaId,Function.identity()));
        return page.map(c->{SesionCaja s=abiertas.get(c.getId());return new OperacionCajaDto(c.getId(),c.getNombre(),
            c.getSucursalId(),c.getSucursal().getNombre(),c.getMoneda().getCodigoIso(),c.isActivo(),
            s==null?EstadoSesionCaja.CLOSED:EstadoSesionCaja.OPEN,s==null?null:s.getId(),
            s==null?null:s.getCodigoVisible());});
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('operaciones_caja.abrir')")
    public SesionCajaDto abrir(UUID cajaId,AperturaCajaInput input){
        BigDecimal fondo=input==null?null:input.fondoInicial();
        if(fondo==null||fondo.signum()<0) throw new ReglaNegocioException("El fondo inicial debe ser mayor o igual que cero.");
        validarImporte(fondo);String nota=nota(input.observacion());
        var p=TenantContext.principalActual();UUID empresa=EmpresaContext.requerirEmpresaId();
        Caja caja=cajas.bloquearParaOperacion(cajaId,p.tenantId(),empresa)
            .orElseThrow(()->new RecursoNoEncontradoException("Caja no encontrada."));
        if(!EmpresaContext.permiteSucursal(caja.getSucursalId())) throw new RecursoNoEncontradoException("Caja no encontrada.");
        if(!caja.isActivo()) throw new ReglaNegocioException("La caja está inactiva y no puede abrirse.");
        if(sessions.findByTenantIdAndEmpresaIdAndCajaIdAndStatus(p.tenantId(),empresa,cajaId,EstadoSesionCaja.OPEN).isPresent())
            throw new ReglaNegocioException("La caja ya tiene un turno abierto.");
        if(sessions.findByTenantIdAndEmpresaIdAndAbiertoPorAndStatus(p.tenantId(),empresa,p.usuarioId(),EstadoSesionCaja.OPEN).isPresent())
            throw new ReglaNegocioException("Ya tienes un turno de caja abierto.");
        LocalDate fecha=LocalDate.now(zonaEmpresa(empresa,p.tenantId()));
        int turno=sessions.ultimoTurno(p.tenantId(),empresa,cajaId,fecha)+1;
        String codigo="TURNO-"+turno+"-"+fecha.format(DateTimeFormatter.ofPattern("ddMMyyyy"))+"-"+caja.getCodigo();
        SesionCaja sesion=new SesionCaja(p.tenantId(),empresa,caja,fecha,turno,codigo,fondo,nota,p.usuarioId());
        try{sessions.saveAndFlush(sesion);}catch(DataIntegrityViolationException ex){
            throw new ReglaNegocioException("No fue posible abrir la caja porque ya existe un turno abierto.",ex);
        }
        auditoria.registrar("CASH_REGISTER_OPENED","SesionCaja",sesion.getId(),detalle(sesion));
        return mapper.toDto(sesion);
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('operaciones_caja.ver')")
    public SesionCajaDto obtener(UUID id){return mapper.toDto(sesionSegura(id));}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('operaciones_caja.ver')")
    public ResumenTurnoCajaDto resumen(UUID id){return calcularResumen(sesionSegura(id));}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('operaciones_caja.ver')")
    public Page<MovimientoTurnoCajaDto> movimientos(UUID id,int pagina,int tamano){
        validarPagina(pagina,tamano);SesionCaja s=sesionSegura(id);
        return movimientos.movimientosSesion(s.getTenantId(),s.getEmpresaId(),id,
            PageRequest.of(pagina,tamano,Sort.by(Sort.Direction.DESC,"creadoEn")))
            .map(m->new MovimientoTurnoCajaDto(m.getId(),m.getCreadoEn(),m.getTipoMovimiento(),m.getMedioPago(),
                m.getConcepto(),m.getMonto(),m.getEstado()));
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('operaciones_caja.cerrar')")
    public SesionCajaDto cerrar(UUID id,CierreCajaInput input){
        if(input==null||input.efectivoContado()==null||input.efectivoContado().signum()<0)
            throw new ReglaNegocioException("El efectivo contado debe ser mayor o igual que cero.");
        validarImporte(input.efectivoContado());String nota=nota(input.observacion());
        var p=TenantContext.principalActual();SesionCaja s=sessions.bloquear(id,p.tenantId(),EmpresaContext.requerirEmpresaId())
            .orElseThrow(()->new RecursoNoEncontradoException("Turno de caja no encontrado."));
        validarSucursal(s);if(s.getStatus()!=EstadoSesionCaja.OPEN) throw new ReglaNegocioException("El turno de caja ya está cerrado.");
        BigDecimal esperado=calcularResumen(s).efectivoEsperado();
        s.cerrar(esperado,input.efectivoContado(),nota,p.usuarioId());sessions.saveAndFlush(s);
        auditoria.registrar("CASH_REGISTER_CLOSED","SesionCaja",s.getId(),detalle(s));
        return mapper.toDto(s);
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('operaciones_caja.ver_historial')")
    public Page<SesionCajaDto> buscarHistorial(String buscar,LocalDate desde,LocalDate hasta,UUID cajaId,
            EstadoRevisionCaja revision,int pagina,int tamano){
        validarPagina(pagina,tamano);if(desde!=null&&hasta!=null&&desde.isAfter(hasta))
            throw new ReglaNegocioException("La fecha desde no puede ser posterior a la fecha hasta.");
        var p=TenantContext.principalActual();
        return sessions.buscarHistorial(p.tenantId(),EmpresaContext.requerirEmpresaId(),limpiar(buscar),desde,hasta,
            cajaId,revision,p.accesoTodasSucursales(),sucursalesPermitidas(p.sucursalIds()),
            PageRequest.of(pagina,tamano,Sort.by(Sort.Direction.DESC,"fechaOperativa","numeroTurno"))).map(mapper::toDto);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('operaciones_caja.revisar_cierre')")
    public SesionCajaDto revisar(UUID id,RevisionCierreCajaInput input){
        if(input==null||input.estado()==null||input.estado()==EstadoRevisionCaja.PENDING)
            throw new ReglaNegocioException("Selecciona un resultado válido para la revisión.");
        String nota=nota(input.observacion());
        if(input.estado()==EstadoRevisionCaja.REQUIRES_REVIEW&&nota==null)
            throw new ReglaNegocioException("La observación es obligatoria cuando el cierre requiere revisión.");
        var p=TenantContext.principalActual();SesionCaja s=sessions.bloquear(id,p.tenantId(),EmpresaContext.requerirEmpresaId())
            .orElseThrow(()->new RecursoNoEncontradoException("Turno de caja no encontrado."));
        validarSucursal(s);if(s.getStatus()!=EstadoSesionCaja.CLOSED)
            throw new ReglaNegocioException("Solo se pueden revisar turnos cerrados.");
        if(s.getEstadoRevision()==EstadoRevisionCaja.APPROVED)
            throw new ReglaNegocioException("Este cierre ya fue aprobado y no admite nuevas revisiones.");
        s.revisar(input.estado(),nota,p.usuarioId());sessions.saveAndFlush(s);
        String evento=input.estado()==EstadoRevisionCaja.APPROVED
            ?"CASH_REGISTER_SESSION_APPROVED":"CASH_REGISTER_SESSION_REQUIRES_REVIEW";
        auditoria.registrar(evento,"SesionCaja",s.getId(),detalle(s));return mapper.toDto(s);
    }

    @Transactional(readOnly=true)
    public UUID requerirSesionAbierta(UUID cajaId){
        var p=TenantContext.principalActual();SesionCaja s=sessions.findByTenantIdAndEmpresaIdAndCajaIdAndStatus(
            p.tenantId(),EmpresaContext.requerirEmpresaId(),cajaId,EstadoSesionCaja.OPEN)
            .orElseThrow(()->new ReglaNegocioException("La caja seleccionada está cerrada. Debes abrirla antes de registrar movimientos."));
        validarSucursal(s);return s.getId();
    }

    @Transactional(readOnly=true)
    public void validarMovimientoModificable(UUID sesionId){
        if(sesionId==null)return;SesionCaja s=sesionSegura(sesionId);
        if(s.getStatus()!=EstadoSesionCaja.OPEN)
            throw new ReglaNegocioException("No se puede modificar un movimiento de un turno de caja cerrado.");
    }

    private ResumenTurnoCajaDto calcularResumen(SesionCaja s){
        BigDecimal entradasEfectivo=BigDecimal.ZERO,salidasEfectivo=BigDecimal.ZERO,tarjeta=BigDecimal.ZERO,
            transferencia=BigDecimal.ZERO,otras=BigDecimal.ZERO,total=BigDecimal.ZERO;
        for(MovimientoFinanciero m:movimientos.movimientosValidosSesion(s.getTenantId(),s.getEmpresaId(),s.getId())){
            if(m.getTipoMovimiento()==TipoMovimientoFinanciero.INCOME){
                total=total.add(m.getMonto());
                if(m.getMedioPago()==MedioPagoMovimiento.CASH)entradasEfectivo=entradasEfectivo.add(m.getMonto());
                else if(m.getMedioPago()==MedioPagoMovimiento.CARD)tarjeta=tarjeta.add(m.getMonto());
                else if(m.getMedioPago()==MedioPagoMovimiento.BANK_TRANSFER)transferencia=transferencia.add(m.getMonto());
                else otras=otras.add(m.getMonto());
            }else if(m.getMedioPago()==MedioPagoMovimiento.CASH) salidasEfectivo=salidasEfectivo.add(m.getMonto());
        }
        return new ResumenTurnoCajaDto(s.getFondoInicial(),entradasEfectivo,salidasEfectivo,
            s.getFondoInicial().add(entradasEfectivo).subtract(salidasEfectivo),tarjeta,transferencia,otras,total);
    }
    private SesionCaja sesionSegura(UUID id){
        if(id==null)throw new RecursoNoEncontradoException("Turno de caja no encontrado.");
        SesionCaja s=sessions.findByIdAndTenantIdAndEmpresaId(id,TenantContext.requerirTenantId(),EmpresaContext.requerirEmpresaId())
            .orElseThrow(()->new RecursoNoEncontradoException("Turno de caja no encontrado."));validarSucursal(s);return s;
    }
    private static void validarSucursal(SesionCaja s){if(!EmpresaContext.permiteSucursal(s.getSucursalId()))
        throw new RecursoNoEncontradoException("Turno de caja no encontrado.");}
    private ZoneId zonaEmpresa(UUID empresa,UUID tenant){String zona=empresas.findByIdAndTenantId(empresa,tenant)
        .map(Empresa::getZonaHoraria).orElse("America/Santo_Domingo");try{return ZoneId.of(zona);}catch(DateTimeException ex){return ZoneId.of("America/Santo_Domingo");}}
    private static Set<UUID> sucursalesPermitidas(Set<UUID> ids){return ids.isEmpty()?Set.of(EMPTY_ID):ids;}
    private static void validarPagina(int pagina,int tamano){if(pagina<0||!PAGE_SIZES.contains(tamano))throw new ReglaNegocioException("Paginación inválida.");}
    private static void validarImporte(BigDecimal v){if(v.scale()>4||v.precision()-v.scale()>15)throw new ReglaNegocioException("El importe excede la precisión permitida.");}
    private static String nota(String v){String n=limpiar(v);if(n.isEmpty())return null;if(n.length()>500)throw new ReglaNegocioException("La observación excede 500 caracteres.");return n;}
    private static String limpiar(String v){return v==null?"":v.trim();}
    private static Sort ordenCaja(String campo,boolean asc){String p=switch(campo==null?"caja":campo){case "sucursal"->"sucursal.nombre";case "moneda"->"moneda.codigoIso";default->"nombre";};return Sort.by(asc?Sort.Direction.ASC:Sort.Direction.DESC,p);}
    private static String detalle(SesionCaja s){return "{\"cajaId\":\""+s.getCajaId()+"\",\"codigo\":\""+s.getCodigoVisible()+"\",\"estado\":\""+s.getStatus()+"\",\"fondoInicial\":"+s.getFondoInicial()+",\"efectivoEsperado\":"+s.getEfectivoEsperado()+",\"efectivoContado\":"+s.getEfectivoContado()+",\"diferencia\":"+s.getDiferencia()+"}";}
}
