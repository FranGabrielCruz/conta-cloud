package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.CatalogoMonedaDto;
import com.citacloud.springboot.contacloud.app.repositories.CatalogoMonedaRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class CurrencyCatalogService {
    private final CatalogoMonedaRepository repository;
    public CurrencyCatalogService(CatalogoMonedaRepository repository) { this.repository = repository; }
    @Transactional(readOnly = true) @PreAuthorize("hasAuthority('MONEDA_VER')")
    public List<CatalogoMonedaDto> listActive() {
        return repository.findAllByActivoTrueOrderByCodigoIso().stream()
            .map(c -> new CatalogoMonedaDto(c.getCodigoIso(), c.getNombre(), c.getSimbolo(), c.getDecimales())).toList();
    }
}
