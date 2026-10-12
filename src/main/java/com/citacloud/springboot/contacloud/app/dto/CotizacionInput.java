package com.citacloud.springboot.contacloud.app.dto;

import java.time.LocalDate;
import java.util.*;

public record CotizacionInput(UUID clienteId,UUID sucursalId,LocalDate fecha,LocalDate validaHasta,UUID monedaId,
        String referencia,String condiciones,List<LineaCotizacionInput> lineas,Long version) {}
