package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.TipoAlmacen;
import java.util.UUID;
public record AlmacenDto(UUID id,String codigo,String nombre,UUID sucursalId,String sucursal,
                         TipoAlmacen tipo,UUID responsableId,String responsable,String direccion,
                         String observacion,boolean activo,boolean predeterminadoSucursal,
                         long productosConExistencia,long version) {}
