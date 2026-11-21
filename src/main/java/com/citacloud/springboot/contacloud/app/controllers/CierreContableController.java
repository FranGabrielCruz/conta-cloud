package com.citacloud.springboot.contacloud.app.controllers;

import com.citacloud.springboot.contacloud.app.dto.CierreContableDto.*;
import com.citacloud.springboot.contacloud.app.services.CierreContablePdfService;
import com.citacloud.springboot.contacloud.app.services.CierreContableService;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounting/closures")
public class CierreContableController {
    private final CierreContableService servicio;
    private final CierreContablePdfService pdf;

    public CierreContableController(CierreContableService servicio,CierreContablePdfService pdf) {
        this.servicio=servicio;
        this.pdf=pdf;
    }

    @GetMapping
    public Lista listar(@RequestParam(required=false) UUID periodId,@RequestParam(required=false) Estado status,
                         @RequestParam(required=false) String search,@RequestParam(defaultValue="0") int page,
                         @RequestParam(defaultValue="10") int pageSize) {
        return servicio.listar(new Filtro(periodId,status,search),page,pageSize);
    }

    @GetMapping("/{id}")
    public Detalle detalle(@PathVariable UUID id) { return servicio.detalle(id); }

    @GetMapping("/{id}/validation")
    public Evaluacion validar(@PathVariable UUID id) { return servicio.validacion(id); }

    @PostMapping("/{id}/close")
    public Cierre cerrar(@PathVariable UUID id,@RequestHeader("Idempotency-Key") UUID clave,@RequestBody Solicitud solicitud) {
        return servicio.cerrar(id,clave,solicitud.observacion());
    }

    @PostMapping("/{id}/reopen")
    public Cierre reabrir(@PathVariable UUID id,@RequestHeader("Idempotency-Key") UUID clave,@RequestBody SolicitudReapertura solicitud) {
        return servicio.reabrir(id,solicitud.cierreId(),clave,solicitud.motivo());
    }

    @GetMapping(value="/{id}/pdf",produces=MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id,@RequestParam(required=false) UUID closeId) {
        var a=pdf.generar(id,closeId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename(a.nombreArchivo()).build().toString())
            .body(a.contenido());
    }
}
