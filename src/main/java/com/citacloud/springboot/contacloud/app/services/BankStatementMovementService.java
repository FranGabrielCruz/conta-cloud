package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.ConciliacionBancariaMapper;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.util.UUID;

@Service
public class BankStatementMovementService {
    private final BankReconciliationRepository reconciliaciones;
    private final BankStatementMovementRepository movimientos;
    private final BankReconciliationMatchRepository asociaciones;
    private final ConciliacionBancariaMapper mapper;
    private final AuditoriaService auditoria;

    public BankStatementMovementService(BankReconciliationRepository reconciliaciones,
            BankStatementMovementRepository movimientos,BankReconciliationMatchRepository asociaciones,
            ConciliacionBancariaMapper mapper,AuditoriaService auditoria){
        this.reconciliaciones=reconciliaciones;this.movimientos=movimientos;this.asociaciones=asociaciones;
        this.mapper=mapper;this.auditoria=auditoria;
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('conciliacion_bancaria.movimiento_banco_editar')")
    public MovimientoEstadoBancarioDto actualizar(UUID conciliacionId,UUID movimientoId,
            MovimientoEstadoBancarioInput input){
        Contexto contexto=cargar(conciliacionId,movimientoId);validarEditable(contexto);
        validarInput(input,contexto.conciliacion());
        MovimientoEstadoBancario movimiento=contexto.movimiento();String anterior=detalle(movimiento);
        movimiento.corregir(input.fecha(),input.direccion(),input.descripcion().trim(),
            limpiarNulo(input.referencia()),normalizar(input.monto(),contexto.conciliacion()));
        movimientos.saveAndFlush(movimiento);
        auditoria.registrar("BANK_RECONCILIATION_BANK_MOVEMENT_UPDATED","MovimientoEstadoBancario",
            movimiento.getId(),"{\"conciliacionId\":\""+conciliacionId+"\",\"anterior\":"+anterior+
                ",\"actual\":"+detalle(movimiento)+"}");
        return mapper.toDto(movimiento);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('conciliacion_bancaria.movimiento_banco_eliminar')")
    public void eliminar(UUID conciliacionId,UUID movimientoId){
        Contexto contexto=cargar(conciliacionId,movimientoId);validarEliminable(contexto);
        MovimientoEstadoBancario movimiento=contexto.movimiento();
        auditoria.registrar("BANK_RECONCILIATION_BANK_MOVEMENT_DELETED","MovimientoEstadoBancario",
            movimiento.getId(),"{\"conciliacionId\":\""+conciliacionId+"\",\"movimiento\":"+detalle(movimiento)+"}");
        movimientos.delete(movimiento);movimientos.flush();
    }

    private Contexto cargar(UUID conciliacionId,UUID movimientoId){
        if(conciliacionId==null||movimientoId==null)throw new RecursoNoEncontradoException("Movimiento bancario no encontrado.");
        var p=TenantContext.principalActual();ConciliacionBancaria conciliacion=reconciliaciones
            .bloquear(conciliacionId,p.tenantId(),p.empresaId())
            .orElseThrow(()->new RecursoNoEncontradoException("Conciliación bancaria no encontrada."));
        MovimientoEstadoBancario movimiento=movimientos.findByIdAndTenantIdAndEmpresaIdAndConciliacionId(
            movimientoId,p.tenantId(),p.empresaId(),conciliacion.getId())
            .orElseThrow(()->new RecursoNoEncontradoException("Movimiento bancario no encontrado."));
        return new Contexto(conciliacion,movimiento);
    }

    private void validarEditable(Contexto contexto){
        validarEstado(contexto.conciliacion());validarManual(contexto.movimiento());
        if(asociaciones.existsByMovimientoBancarioId(contexto.movimiento().getId()))
            throw new ReglaNegocioException("El movimiento bancario ya fue conciliado y no puede modificarse.");
    }
    private void validarEliminable(Contexto contexto){
        validarEstado(contexto.conciliacion());validarManual(contexto.movimiento());
        if(asociaciones.existsByMovimientoBancarioId(contexto.movimiento().getId()))
            throw new ReglaNegocioException("El movimiento bancario ya fue conciliado y no puede eliminarse.");
    }
    private static void validarEstado(ConciliacionBancaria conciliacion){
        if(conciliacion.getStatus()==EstadoConciliacionBancaria.FINALIZED)
            throw new ReglaNegocioException("La conciliación está finalizada y no puede modificarse.");
        if(conciliacion.getStatus()==EstadoConciliacionBancaria.VOIDED)
            throw new ReglaNegocioException("La conciliación está anulada y no puede modificarse.");
    }
    private static void validarManual(MovimientoEstadoBancario movimiento){
        if(movimiento.getFuente()!=FuenteMovimientoBancario.MANUAL)
            throw new ReglaNegocioException("Solo pueden corregirse movimientos bancarios capturados manualmente.");
    }
    private static void validarInput(MovimientoEstadoBancarioInput input,ConciliacionBancaria conciliacion){
        if(input==null||input.fecha()==null)throw new ReglaNegocioException("Selecciona una fecha.");
        if(input.fecha().isBefore(conciliacion.getFechaInicial())||input.fecha().isAfter(conciliacion.getFechaFinal()))
            throw new ReglaNegocioException("La fecha del movimiento debe pertenecer al período de conciliación.");
        if(input.direccion()==null)throw new ReglaNegocioException("Selecciona un tipo de movimiento.");
        String descripcion=input.descripcion()==null?"":input.descripcion().trim();
        if(descripcion.isEmpty())throw new ReglaNegocioException("Ingresa una descripción.");
        if(descripcion.length()>180)throw new ReglaNegocioException("La descripción excede 180 caracteres.");
        if(input.monto()==null||input.monto().signum()<=0)throw new ReglaNegocioException("Ingresa un monto mayor que cero.");
        if(input.referencia()!=null&&input.referencia().trim().length()>100)
            throw new ReglaNegocioException("La referencia excede 100 caracteres.");
    }
    private static BigDecimal normalizar(BigDecimal monto,ConciliacionBancaria conciliacion){
        int escala=Math.max(0,Math.min(4,conciliacion.getMoneda().getDecimales()));
        return monto.setScale(escala,RoundingMode.HALF_UP);
    }
    private static String limpiarNulo(String valor){String limpio=valor==null?"":valor.trim();return limpio.isEmpty()?null:limpio;}
    private static String detalle(MovimientoEstadoBancario m){return "{\"bankAccountId\":\""+m.getCuentaBancariaId()+
        "\",\"fecha\":\""+m.getFecha()+"\",\"direccion\":\""+m.getDirection()+"\",\"monto\":\""+
        m.getAmount()+"\",\"referencia\":"+(m.getReference()==null?"null":"\""+escapar(m.getReference())+"\"")+"}";}
    private static String escapar(String valor){return valor.replace("\\","\\\\").replace("\"","\\\"")
        .replace("\r","\\r").replace("\n","\\n");}
    private record Contexto(ConciliacionBancaria conciliacion,MovimientoEstadoBancario movimiento){}
}
