package com.citacloud.springboot.contacloud.app.dto;

import com.citacloud.springboot.contacloud.app.models.TipoProveedor;
import java.util.UUID;

public record ProveedorInput(String nombreComercial,String razonSocial,String identificacionFiscal,
    TipoProveedor tipo,String telefono,String correo,UUID condicionPagoId,UUID monedaId,String direccion,
    String contacto,String telefonoContacto,String notas,boolean activo) {}
