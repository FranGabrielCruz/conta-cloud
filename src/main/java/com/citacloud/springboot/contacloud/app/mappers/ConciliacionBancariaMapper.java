package com.citacloud.springboot.contacloud.app.mappers;
import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class ConciliacionBancariaMapper {
    public ConciliacionBancaria toEntity(ConciliacionBancariaInput i,UUID tenantId,UUID empresaId,
            CuentaBancaria cuenta,UUID usuarioId){
        var e=new ConciliacionBancaria(tenantId,empresaId,cuenta.getId(),cuenta.getMonedaId(),i.fechaInicial(),
            i.fechaFinal(),i.saldoInicialBanco(),i.saldoFinalBanco(),usuarioId);
        e.asignarRelaciones(cuenta,cuenta.getMoneda());return e;
    }
    public ConciliacionBancariaDto toDto(ConciliacionBancaria e){
        CuentaBancaria c=e.getCuentaBancaria();Moneda m=e.getMoneda();
        return new ConciliacionBancariaDto(e.getId(),e.getCuentaBancariaId(),etiqueta(c),e.getMonedaId(),
            m.getCodigoIso(),m.getDecimales(),e.getFechaInicial(),e.getFechaFinal(),e.getSaldoInicialBanco(),
            e.getSaldoFinalBanco(),e.getStatus(),e.getCreadoEn(),e.getFinalizadoEn(),e.getFinalizadoPor(),
            nombre(e.getUsuarioFinalizacion()),e.getAnuladoEn(),e.getAnuladoPor(),nombre(e.getUsuarioAnulacion()),e.getMotivoAnulacion());
    }
    public MovimientoEstadoBancarioDto toDto(MovimientoEstadoBancario e){return new MovimientoEstadoBancarioDto(
        e.getId(),e.getFecha(),e.getDirection(),e.getDescription(),e.getReference(),e.getAmount(),e.getFuente());}
    public static String etiqueta(CuentaBancaria c){String last=c.getNumeroCuentaUltimos4();return c.getBancoNombre()+" · "+
        c.getNombreCuenta()+(last==null||last.isBlank()?"":" · •••• "+last);}
    private static String nombre(Usuario u){return u==null?null:(u.getNombre()+" "+u.getApellido()).trim();}
}
