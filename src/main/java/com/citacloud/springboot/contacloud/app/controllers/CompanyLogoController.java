package com.citacloud.springboot.contacloud.app.controllers;

import com.citacloud.springboot.contacloud.app.services.CompanyConfigurationService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CompanyLogoController {
    private final CompanyConfigurationService service;

    public CompanyLogoController(CompanyConfigurationService service) { this.service = service; }

    @GetMapping("/api/company/logo")
    public ResponseEntity<byte[]> currentLogo() {
        var logo = service.currentLogo();
        if (logo.isEmpty()) return ResponseEntity.notFound().cacheControl(CacheControl.noStore()).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(logo.get().mimeType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(logo.get().bytes());
    }
}
