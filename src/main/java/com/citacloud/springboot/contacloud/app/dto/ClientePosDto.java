package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.TipoIdentificacionCliente;
import java.util.UUID;
public record ClientePosDto(UUID id,String codigo,String nombre,TipoIdentificacionCliente tipoIdentificacion,
                            String identificacion,String telefono){
    @Override public String toString(){return codigo+" · "+nombre+(identificacion==null||identificacion.isBlank()?"":" · "+identificacion);}
}
