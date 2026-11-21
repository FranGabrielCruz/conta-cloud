package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.EventoContable;
import com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.Modulo;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class RegistroIntegracionesContables {
 private final Map<Modulo,Map<String,IntegracionContableHandler>> handlers=new EnumMap<>(Modulo.class);
 public RegistroIntegracionesContables(List<IntegracionContableHandler> integraciones) {
  for(var h:integraciones)for(String evento:h.eventos()) {
   if(handlers.computeIfAbsent(h.modulo(),m->new HashMap<>()).putIfAbsent(evento,h)!=null)
    throw new IllegalStateException("Integración contable duplicada: "+h.modulo()+"/"+evento);
  }
 }
 public boolean disponible(Modulo modulo) { return handlers.containsKey(modulo); }
 public IntegracionContableHandler resolver(EventoContable e) {
  var modulo=Modulo.valueOf(e.fuente().origen().name());
  var h=handlers.getOrDefault(modulo,Map.of()).get(e.fuente().tipo()+":"+e.fuente().evento());
  if(h==null)throw new ReglaNegocioException(com.citacloud.springboot.contacloud.app.errors.ErrorCode.INTEGRATION_NOT_IMPLEMENTED,
   "La integración de este evento contable todavía no está disponible.");
  return h;
 }
}

