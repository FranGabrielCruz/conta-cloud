package com.citacloud.springboot.contacloud.app.mappers;
import com.citacloud.springboot.contacloud.app.dto.TransferenciaFinancieraDto;
import com.citacloud.springboot.contacloud.app.models.*;
import org.springframework.stereotype.Component;
@Component
public class TransferenciaFinancieraMapper {
    public TransferenciaFinancieraDto toDto(TransferenciaFinanciera t){
        String origen=t.getTipoCuentaOrigen()==TipoCuentaDinero.CASH_REGISTER?t.getCajaOrigen().getNombre():
            t.getCuentaBancariaOrigen().getBancoNombre()+" · "+t.getCuentaBancariaOrigen().getNombreCuenta();
        String destino=t.getTipoCuentaDestino()==TipoCuentaDinero.CASH_REGISTER?t.getCajaDestino().getNombre():
            t.getCuentaBancariaDestino().getBancoNombre()+" · "+t.getCuentaBancariaDestino().getNombreCuenta();
        String usuario=t.getUsuarioAnulacion()==null?null:
            (t.getUsuarioAnulacion().getNombre()+" "+t.getUsuarioAnulacion().getApellido()).trim();
        return new TransferenciaFinancieraDto(t.getId(),t.getFecha(),t.getTipoCuentaOrigen(),
            t.getTipoCuentaOrigen()==TipoCuentaDinero.CASH_REGISTER?t.getCajaOrigenId():t.getCuentaBancariaOrigenId(),origen,
            t.getTipoCuentaDestino(),t.getTipoCuentaDestino()==TipoCuentaDinero.CASH_REGISTER?t.getCajaDestinoId():t.getCuentaBancariaDestinoId(),destino,
            t.getMonedaOrigenId(),t.getMonedaOrigen().getCodigoIso(),t.getMonedaDestinoId(),t.getMonedaDestino().getCodigoIso(),
            t.getMontoOrigen(),t.getMontoDestino(),t.getTasaCambio(),t.getDescripcionTasa(),t.getReferencia(),t.getDescripcion(),
            t.getEstado(),t.getCreadoEn(),t.getAnuladoEn(),usuario,t.getMotivoAnulacion(),
            t.getEstado()==EstadoMovimientoFinanciero.REGISTERED);
    }
}
