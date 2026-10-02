package com.citacloud.springboot.contacloud.app.mappers;
import com.citacloud.springboot.contacloud.app.dto.MovimientoFinancieroDto;
import com.citacloud.springboot.contacloud.app.models.*;
import org.springframework.stereotype.Component;
@Component
public class MovimientoFinancieroMapper {
    public MovimientoFinancieroDto toDto(MovimientoFinanciero m) {
        boolean manualRegistrado=m.getTipoOrigen()==TipoOrigenMovimiento.MANUAL
            && m.getEstado()==EstadoMovimientoFinanciero.REGISTERED;
        String cuenta=m.getTipoCuenta()==TipoCuentaDinero.CASH_REGISTER
            ? m.getCaja().getNombre()
            : m.getCuentaBancaria().getBancoNombre()+" · "+m.getCuentaBancaria().getNombreCuenta();
        String usuario=m.getUsuarioAnulacion()==null?null:
            (m.getUsuarioAnulacion().getNombre()+" "+m.getUsuarioAnulacion().getApellido()).trim();
        return new MovimientoFinancieroDto(m.getId(),m.getTipoMovimiento(),m.getFecha(),m.getTipoCuenta(),
            m.getTipoCuenta()==TipoCuentaDinero.CASH_REGISTER?m.getCajaId():m.getCuentaBancariaId(),
            cuenta,m.getMonedaId(),m.getMoneda().getCodigoIso(),m.getMonto(),m.getConcepto(),m.getReferencia(),
            m.getDescripcion(),m.getEstado(),m.getTipoOrigen(),m.getCreadoEn(),m.getAnuladoEn(),usuario,
            m.getMotivoAnulacion(),manualRegistrado,manualRegistrado,m.getMedioPago(),m.getSesionCajaId());
    }
}
