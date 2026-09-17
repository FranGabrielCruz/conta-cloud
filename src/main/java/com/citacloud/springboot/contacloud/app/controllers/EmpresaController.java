package com.citacloud.springboot.contacloud.app.controllers;

import com.citacloud.springboot.contacloud.app.dto.EmpresaDto;
import com.citacloud.springboot.contacloud.app.services.EmpresaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/empresa-actual")
public class EmpresaController {
    private final EmpresaService service;
    public EmpresaController(EmpresaService service) { this.service = service; }
    @GetMapping public EmpresaDto obtener() { return service.obtenerActual(); }
    @PutMapping public ResponseEntity<EmpresaDto> actualizar(@Valid @RequestBody EmpresaDto dto) {
        return ResponseEntity.ok(service.actualizarActual(dto));
    }
}
