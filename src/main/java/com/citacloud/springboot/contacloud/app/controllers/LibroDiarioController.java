package com.citacloud.springboot.contacloud.app.controllers;

import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.Origen;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.Tipo;
import com.citacloud.springboot.contacloud.app.dto.LibroDiarioDto.Filtro;
import com.citacloud.springboot.contacloud.app.dto.LibroDiarioDto.Resultado;
import com.citacloud.springboot.contacloud.app.services.LibroDiarioService;
import com.citacloud.springboot.contacloud.app.services.LibroDiarioPdfService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounting/general-journal")
public class LibroDiarioController {
    private final LibroDiarioService servicio;
    private final LibroDiarioPdfService pdf;

    public LibroDiarioController(LibroDiarioService servicio,LibroDiarioPdfService pdf) {
        this.servicio=servicio;
        this.pdf=pdf;
    }

    @GetMapping
    public Resultado consultar(
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fromDate,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate toDate,
        @RequestParam(required=false) Tipo type,
        @RequestParam(required=false) Origen origin,
        @RequestParam(required=false) String search,
        @RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="10") int pageSize) {
        return servicio.consultar(new Filtro(fromDate,toDate,type,origin,search),page,pageSize);
    }

    @GetMapping(value="/pdf",produces=MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportar(
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fromDate,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate toDate,
        @RequestParam(required=false) Tipo type,
        @RequestParam(required=false) Origen origin,
        @RequestParam(required=false) String search) {
        var archivo=pdf.generar(new Filtro(fromDate,toDate,type,origin,search));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename(archivo.nombreArchivo()).build().toString())
            .body(archivo.contenido());
    }
}
