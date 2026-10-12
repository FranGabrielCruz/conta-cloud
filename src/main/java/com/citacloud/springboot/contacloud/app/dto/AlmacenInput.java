package com.citacloud.springboot.contacloud.app.dto;
import com.citacloud.springboot.contacloud.app.models.TipoAlmacen;
import java.util.UUID;
public record AlmacenInput(String nombre,UUID sucursalId,TipoAlmacen tipo,UUID responsableId,
                           String direccion,String observacion,boolean predeterminadoSucursal,Long version) {}
