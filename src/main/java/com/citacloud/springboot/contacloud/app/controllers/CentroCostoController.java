package com.citacloud.springboot.contacloud.app.controllers;

import com.citacloud.springboot.contacloud.app.dto.CentroCostoDto.*;
import com.citacloud.springboot.contacloud.app.services.CentroCostoService;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/accounting/cost-centers")
public class CentroCostoController {
    private final CentroCostoService servicio;

    public CentroCostoController(CentroCostoService servicio) { this.servicio=servicio; }

    @GetMapping
    public Resultado listar(@RequestParam(required=false) Boolean active,@RequestParam(required=false) String search,
                            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int pageSize) {
        return servicio.listar(new Filtro(active,search),page,pageSize);
    }

    @GetMapping("/{id}")
    public Centro detalle(@PathVariable UUID id) { return servicio.detalle(id); }

    @PostMapping
    public ResponseEntity<Centro> crear(@RequestHeader("Idempotency-Key") UUID clave,@RequestBody Entrada entrada) {
        var id=servicio.crear(entrada,clave);
        URI ubicacion=ServletUriComponentsBuilder.fromCurrentRequest().pathSegment(id.toString()).build().toUri();
        return ResponseEntity.created(ubicacion).body(servicio.detalle(id));
    }

    @PutMapping("/{id}")
    public Centro editar(@PathVariable UUID id,@RequestParam long version,@RequestBody Entrada entrada) {
        servicio.editar(id,version,entrada);
        return servicio.detalle(id);
    }

    @PostMapping("/{id}/activar")
    public Centro activar(@PathVariable UUID id,@RequestParam long version) {
        servicio.activar(id,version);
        return servicio.detalle(id);
    }

    @PostMapping("/{id}/desactivar")
    public Centro desactivar(@PathVariable UUID id,@RequestParam long version) {
        servicio.desactivar(id,version);
        return servicio.detalle(id);
    }
}
