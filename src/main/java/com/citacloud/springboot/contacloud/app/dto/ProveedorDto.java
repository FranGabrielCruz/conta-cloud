package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.TipoProveedor;
import java.util.UUID;

public record ProveedorDto(UUID id,String nombreComercial,String razonSocial,String identificacionFiscal,
    TipoProveedor tipo,String telefono,String correo,UUID condicionPagoId,String condicionPagoNombre,
    UUID monedaId,String monedaCodigo,String direccion,String contacto,String telefonoContacto,String notas,
    boolean activo) {
    public String nombreVisible(){return nombreComercial!=null&&!nombreComercial.isBlank()?nombreComercial:razonSocial;}
}
