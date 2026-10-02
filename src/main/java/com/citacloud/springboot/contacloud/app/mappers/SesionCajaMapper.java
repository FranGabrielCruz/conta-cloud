package com.citacloud.springboot.contacloud.app.mappers;
import com.citacloud.springboot.contacloud.app.dto.SesionCajaDto;
import com.citacloud.springboot.contacloud.app.models.*;
import org.springframework.stereotype.Component;
@Component
public class SesionCajaMapper {
    public SesionCajaDto toDto(SesionCaja s){return new SesionCajaDto(s.getId(),s.getCajaId(),s.getCaja().getNombre(),
        s.getSucursalId(),s.getSucursal().getNombre(),s.getMoneda().getCodigoIso(),s.getFechaOperativa(),s.getNumeroTurno(),
        s.getCodigoVisible(),s.getAbiertoEn(),nombre(s.getUsuarioApertura()),s.getFondoInicial(),s.getObservacionApertura(),
        s.getCerradoEn(),nombre(s.getUsuarioCierre()),s.getEfectivoEsperado(),s.getEfectivoContado(),s.getDiferencia(),
        s.getObservacionCierre(),s.getStatus(),s.getEstadoRevision(),s.getRevisadoEn(),nombre(s.getUsuarioRevision()),
        s.getObservacionRevision());}
    private static String nombre(Usuario u){return u==null?null:(u.getNombre()+" "+u.getApellido()).trim();}
}
