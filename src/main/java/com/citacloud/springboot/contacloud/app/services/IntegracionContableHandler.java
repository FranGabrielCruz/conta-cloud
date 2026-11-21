package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.EventoContable;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.LineaConfigurada;
import com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.Modulo;
import java.util.*;

public interface IntegracionContableHandler {
 Modulo modulo();
 Set<String> eventos();
 List<LineaConfigurada> proponer(EventoContable evento);
}

