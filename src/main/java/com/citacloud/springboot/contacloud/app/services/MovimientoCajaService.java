package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.time.*;
import java.util.*;

@Service
public class MovimientoCajaService {
    private static final String CREATE_OPERATION="CASH_MOVEMENT_CREATE";
    private static final String REVERSE_OPERATION="CASH_MOVEMENT_REVERSE";
    private static final Set<Integer> PAGE_SIZES=Set.of(5,10,20,50);
    private final CashRegisterSessionRepository sesiones;
    private final FinancialMovementRepository movimientos;
    private final ConceptoMovimientoCajaRepository conceptos;
    private final IdempotentOperationService idempotencia;
    private final AuditoriaService auditoria;
    private final JdbcTemplate jdbc;

    public MovimientoCajaService(CashRegisterSessionRepository sesiones,FinancialMovementRepository movimientos,
            ConceptoMovimientoCajaRepository conceptos,
            IdempotentOperationService idempotencia,AuditoriaService auditoria,JdbcTemplate jdbc){
        this.sesiones=sesiones;this.movimientos=movimientos;this.conceptos=conceptos;
        this.idempotencia=idempotencia;this.auditoria=auditoria;this.jdbc=jdbc;
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('movimientos_caja.ver')")
    public MovimientoCajaContextoDto contexto(UUID sesionId){
        SesionCaja sesion=sesionPos(sesionId,false);var p=TenantContext.principalActual();
        List<ConceptoMovimientoCajaDto> opciones=conceptos
            .findAllByTenantIdAndEmpresaIdAndActiveTrueOrderByTipoAscNombreAsc(p.tenantId(),p.empresaId()).stream()
            .map(c->new ConceptoMovimientoCajaDto(c.getId(),c.getCodigo(),c.getNombre(),c.getTipo(),c.isRequiereObservacion())).toList();
        return new MovimientoCajaContextoDto(sesion.getId(),sesion.getCaja().getNombre(),sesion.getSucursal().getNombre(),
            sesion.getMoneda().getCodigoIso(),sesion.getStatus()==EstadoSesionCaja.OPEN,opciones);
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('movimientos_caja.ver')")
    public Page<MovimientoCajaDto> buscar(UUID sesionId,String buscar,TipoMovimientoFinanciero tipo,
            TipoOrigenMovimiento origen,int pagina,int tamano){
        validarPagina(pagina,tamano);SesionCaja sesion=sesionPos(sesionId,false);var p=TenantContext.principalActual();
        return movimientos.buscarMovimientosCaja(p.tenantId(),p.empresaId(),sesion.getId(),limpiar(buscar),
            tipo==null?"ALL":tipo.name(),origen==null?"ALL":origen.name(),PageRequest.of(pagina,tamano)).map(this::mapear);
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('movimientos_caja.ver')")
    public MovimientoCajaDto obtener(UUID sesionId,UUID movimientoId){
        SesionCaja sesion=sesionPos(sesionId,false);var p=TenantContext.principalActual();
        MovimientoCajaProjection fila=movimientos.detalleMovimientoCaja(p.tenantId(),p.empresaId(),sesion.getId(),movimientoId)
            .orElseThrow(()->new RecursoNoEncontradoException("Movimiento de caja no encontrado."));
        return mapear(fila);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('movimientos_caja.crear')")
    public MovimientoCajaDto crear(MovimientoCajaInput input){
        validarEntrada(input);var p=TenantContext.principalActual();String huella=CommandFingerprint.sha256(input);
        var inicio=idempotencia.begin(p.tenantId(),p.empresaId(),CREATE_OPERATION,input.idempotencia(),huella);
        if(inicio.replay())return obtenerInterno(input.sesionId(),inicio.resultId());
        SesionCaja sesion=sesionBloqueada(input.sesionId());
        ConceptoMovimientoCaja concepto=conceptos.findByIdAndTenantIdAndEmpresaIdAndActiveTrue(input.conceptoId(),p.tenantId(),p.empresaId())
            .orElseThrow(()->new ReglaNegocioException("El concepto seleccionado no está disponible para este tipo de movimiento."));
        if(concepto.getTipo()!=input.tipo())throw new ReglaNegocioException("El concepto seleccionado no está disponible para este tipo de movimiento.");
        String observacion=opcional(input.observacion(),500);
        if(concepto.isRequiereObservacion()&&observacion==null)throw new ReglaNegocioException("La observación es obligatoria para el concepto seleccionado.");
        String referencia=referenciaManual(input.referencia());String numero=siguiente(p.tenantId(),p.empresaId());
        MovimientoFinanciero movimiento=MovimientoFinanciero.manualCaja(p.tenantId(),p.empresaId(),input.tipo(),
            sesion.getFechaOperativa(),sesion.getCajaId(),sesion.getMonedaId(),
            escala(input.importe()),numero,concepto.getId(),concepto.getNombre(),referencia,observacion,sesion.getId(),p.usuarioId());
        try{movimiento=movimientos.saveAndFlush(movimiento);}catch(DataIntegrityViolationException ex){
            throw new ReglaNegocioException("El estado de la caja cambió. Actualice la información e intente nuevamente.",ex);}
        auditoria.registrar("CASH_MOVEMENT_CREATED","MovimientoFinanciero",movimiento.getId(),detalle(movimiento));
        idempotencia.complete(p.tenantId(),p.empresaId(),CREATE_OPERATION,input.idempotencia(),"CASH_MOVEMENT",movimiento.getId());
        return obtenerInterno(sesion.getId(),movimiento.getId());
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('movimientos_caja.revertir')")
    public MovimientoCajaDto revertir(ReversionMovimientoCajaInput input){
        if(input==null||input.sesionId()==null||input.movimientoId()==null||input.idempotencia()==null)
            throw new ReglaNegocioException("No fue posible identificar el movimiento de caja.");
        String motivo=requerido(input.motivo(),500,"El motivo de reversión es obligatorio.");var p=TenantContext.principalActual();
        String huella=CommandFingerprint.sha256(input);var inicio=idempotencia.begin(p.tenantId(),p.empresaId(),REVERSE_OPERATION,input.idempotencia(),huella);
        if(inicio.replay())return obtenerInterno(input.sesionId(),inicio.resultId());
        SesionCaja sesion=sesionBloqueada(input.sesionId());
        MovimientoFinanciero original=movimientos.bloquearMovimientoCaja(input.movimientoId(),p.tenantId(),p.empresaId(),sesion.getId())
            .orElseThrow(()->new RecursoNoEncontradoException("Movimiento de caja no encontrado."));
        if(original.getTipoOrigen()!=TipoOrigenMovimiento.MANUAL||original.getEstado()!=EstadoMovimientoFinanciero.REGISTERED)
            throw new ReglaNegocioException("Este movimiento debe revertirse desde su documento de origen.");
        if(movimientos.existsByTenantIdAndEmpresaIdAndReversionDeMovimientoId(p.tenantId(),p.empresaId(),original.getId()))
            throw new ReglaNegocioException("El movimiento ya fue revertido.");
        MovimientoFinanciero reverso=MovimientoFinanciero.reversionCaja(original,
            sesion.getFechaOperativa(),siguiente(p.tenantId(),p.empresaId()),motivo,p.usuarioId());
        try{reverso=movimientos.saveAndFlush(reverso);}catch(DataIntegrityViolationException ex){
            throw new ReglaNegocioException("El estado de la caja cambió. Actualice la información e intente nuevamente.",ex);}
        auditoria.registrar("CASH_MOVEMENT_REVERSED","MovimientoFinanciero",reverso.getId(),
            "{\"originalId\":\""+original.getId()+"\",\"reversalId\":\""+reverso.getId()+"\"}");
        idempotencia.complete(p.tenantId(),p.empresaId(),REVERSE_OPERATION,input.idempotencia(),"CASH_MOVEMENT",reverso.getId());
        return obtenerInterno(sesion.getId(),reverso.getId());
    }

    private MovimientoCajaDto obtenerInterno(UUID sesionId,UUID movimientoId){var p=TenantContext.principalActual();
        return movimientos.detalleMovimientoCaja(p.tenantId(),p.empresaId(),sesionId,movimientoId)
            .map(this::mapear).orElseThrow(()->new RecursoNoEncontradoException("Movimiento de caja no encontrado."));}
    private SesionCaja sesionPos(UUID id,boolean abierta){var p=TenantContext.principalActual();SesionCaja s=sesiones
        .findByIdAndTenantIdAndEmpresaId(id,p.tenantId(),p.empresaId())
        .orElseThrow(()->new RecursoNoEncontradoException("Turno de caja no encontrado."));validarSesionPos(s,abierta);return s;}
    private SesionCaja sesionBloqueada(UUID id){var p=TenantContext.principalActual();SesionCaja s=sesiones
        .bloquear(id,p.tenantId(),p.empresaId()).orElseThrow(()->new RecursoNoEncontradoException("Turno de caja no encontrado."));
        validarSesionPos(s,true);return s;}
    private static void validarSesionPos(SesionCaja s,boolean exigirAbierta){var p=TenantContext.principalActual();
        if(!EmpresaContext.permiteSucursal(s.getSucursalId())||!p.usuarioId().equals(s.getAbiertoPor()))
            throw new RecursoNoEncontradoException("Turno de caja no encontrado.");
        if(exigirAbierta&&s.getStatus()!=EstadoSesionCaja.OPEN)
            throw new ReglaNegocioException("La caja debe estar abierta para registrar un movimiento.");}
    private MovimientoCajaDto mapear(MovimientoCajaProjection x){TipoOrigenMovimiento origen=TipoOrigenMovimiento.valueOf(x.getOrigen());
        EstadoMovimientoFinanciero estado=EstadoMovimientoFinanciero.valueOf(x.getEstado());
        OffsetDateTime fechaHora=x.getFechaHora()==null?null:x.getFechaHora().atOffset(ZoneOffset.UTC);
        return new MovimientoCajaDto(x.getId(),x.getNumero(),fechaHora,x.getCaja(),x.getSucursal(),x.getUsuario(),
            TipoMovimientoFinanciero.valueOf(x.getTipo()),origen,x.getConcepto(),x.getReferencia(),x.getImporte(),x.getSaldo(),
            x.getMonedaCodigo(),x.getObservacion(),estado,x.getReversionDeId(),x.isReversible());}
    private String siguiente(UUID tenant,UUID empresa){Long valor=jdbc.queryForObject("""
        INSERT INTO cash_movement_sequence(tenant_id,empresa_id,current_value) VALUES(?,?,1)
        ON CONFLICT(tenant_id,empresa_id) DO UPDATE SET current_value=cash_movement_sequence.current_value+1
        RETURNING current_value
        """,Long.class,tenant,empresa);return "MOV-"+String.format(Locale.ROOT,"%06d",valor);}
    private static void validarEntrada(MovimientoCajaInput i){if(i==null||i.sesionId()==null||i.tipo()==null||i.conceptoId()==null||i.idempotencia()==null)
        throw new ReglaNegocioException("Complete los datos obligatorios del movimiento.");if(i.importe()==null||i.importe().signum()<=0)
        throw new ReglaNegocioException("El importe debe ser mayor que cero.");if(i.importe().scale()>4||i.importe().precision()-i.importe().scale()>15)
        throw new ReglaNegocioException("El importe excede la precisión permitida.");}
    private static String referenciaManual(String valor){String r=opcional(valor,100);if(r!=null&&r.matches("(?i)^(FAC|REC|VAL)-.*"))
        throw new ReglaNegocioException("La referencia indicada está reservada para documentos generados por el sistema.");return r;}
    private static BigDecimal escala(BigDecimal valor){return valor.setScale(4,RoundingMode.HALF_UP);}
    private static String requerido(String valor,int max,String mensaje){String v=limpiar(valor);if(v.isEmpty())throw new ReglaNegocioException(mensaje);if(v.length()>max)throw new ReglaNegocioException("El valor excede "+max+" caracteres.");return v;}
    private static String opcional(String valor,int max){String v=limpiar(valor);if(v.isEmpty())return null;if(v.length()>max)throw new ReglaNegocioException("El valor excede "+max+" caracteres.");return v;}
    private static String limpiar(String valor){return valor==null?"":valor.trim();}
    private static void validarPagina(int pagina,int tamano){if(pagina<0||!PAGE_SIZES.contains(tamano))throw new ReglaNegocioException("Paginación inválida.");}
    private static String detalle(MovimientoFinanciero m){return "{\"numero\":\""+m.numeroVisibleCaja()+"\",\"tipo\":\""+m.getTipoMovimiento()+"\",\"monto\":"+m.getMonto()+"}";}
}
