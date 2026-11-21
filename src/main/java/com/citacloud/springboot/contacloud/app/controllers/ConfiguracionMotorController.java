package com.citacloud.springboot.contacloud.app.controllers;
import com.citacloud.springboot.contacloud.app.dto.ConfiguracionMotorDto.*;
import com.citacloud.springboot.contacloud.app.services.ConfiguracionMotorService;
import java.util.List;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/accounting/configuration")
public class ConfiguracionMotorController {
 private final ConfiguracionMotorService servicio;
 public ConfiguracionMotorController(ConfiguracionMotorService servicio) { this.servicio=servicio; }
 @GetMapping public Estado obtener() { return servicio.obtener(); }
 @PutMapping public Estado guardar(@RequestBody Entrada entrada) { return servicio.guardar(entrada); }
 @PostMapping("/evaluation") public Estado evaluar(@RequestBody Entrada entrada) { return servicio.evaluar(entrada); }
 @GetMapping("/accounts/lookup")
 public List<Cuenta> buscar(@RequestParam String function,@RequestParam(defaultValue="") String search,
  @RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="30") int limit) {
  return servicio.buscar(function,search,offset,limit);
 }
}

