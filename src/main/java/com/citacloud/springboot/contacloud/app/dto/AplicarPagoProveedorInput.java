package com.citacloud.springboot.contacloud.app.dto;
import java.util.List;
public record AplicarPagoProveedorInput(List<PagoProveedorInput.Aplicacion> aplicaciones,Long version){}
