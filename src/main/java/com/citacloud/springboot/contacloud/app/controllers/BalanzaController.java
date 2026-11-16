package com.citacloud.springboot.contacloud.app.controllers;

import com.citacloud.springboot.contacloud.app.dto.BalanzaDto.Filtro;
import com.citacloud.springboot.contacloud.app.dto.BalanzaDto.Resultado;
import com.citacloud.springboot.contacloud.app.services.BalanzaPdfService;
import com.citacloud.springboot.contacloud.app.services.BalanzaService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounting/trial-balance")
public class BalanzaController {
    private final BalanzaService servicio;
    private final BalanzaPdfService pdf;

    public BalanzaController(BalanzaService servicio,BalanzaPdfService pdf) {
        this.servicio=servicio;
        this.pdf=pdf;
    }

    @GetMapping
    public Resultado consultar(@ModelAttribute Filtro filtro,@RequestParam(defaultValue="0") int page,
                               @RequestParam(defaultValue="10") int pageSize) {
        return servicio.consultar(filtro,page,pageSize);
    }

    @GetMapping(value="/pdf",produces=MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportar(@ModelAttribute Filtro filtro) {
        var archivo=pdf.generar(filtro);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename(archivo.nombreArchivo()).build().toString())
            .body(archivo.contenido());
    }
}
