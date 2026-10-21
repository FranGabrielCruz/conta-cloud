package com.citacloud.springboot.contacloud.app.dto;
import java.util.*;
public record SolicitudVistaPreviaDescuentoInput(UUID operacionId,DescuentoVentaInput descuento,List<LineaVistaPreviaDescuentoInput> lineas){}
