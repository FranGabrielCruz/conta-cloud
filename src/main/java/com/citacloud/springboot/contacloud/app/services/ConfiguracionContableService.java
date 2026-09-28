package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.ConfiguracionContableDto;
import com.citacloud.springboot.contacloud.app.dto.ConfiguracionContableInput;
import com.citacloud.springboot.contacloud.app.mappers.ConfiguracionContableMapper;
import com.citacloud.springboot.contacloud.app.models.ConfiguracionContable;
import com.citacloud.springboot.contacloud.app.repositories.ConfiguracionContableRepository;
import com.citacloud.springboot.contacloud.app.repositories.MonedaRepository;
import com.citacloud.springboot.contacloud.app.security.EmpresaContext;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class ConfiguracionContableService {
    private final ConfiguracionContableRepository repository;
    private final MonedaRepository monedas;
    private final ConfiguracionContableMapper mapper;
    private final AuditoriaService auditoria;

    public ConfiguracionContableService(ConfiguracionContableRepository repository,
                                        MonedaRepository monedas,
                                        ConfiguracionContableMapper mapper,
                                        AuditoriaService auditoria) {
        this.repository = repository;
        this.monedas = monedas;
        this.mapper = mapper;
        this.auditoria = auditoria;
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CONFIGURACION_CONTABLE') and hasAnyAuthority('configuracion_contable.ver','CONFIGURACION_CONTABLE_VER')")
    public ConfiguracionContableDto obtener() {
        return mapper.toDto(configuracionActual());
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('CONFIGURACION_CONTABLE') and hasAnyAuthority('configuracion_contable.editar','CONFIGURACION_CONTABLE_EDITAR')")
    public ConfiguracionContableDto actualizar(ConfiguracionContableInput input) {
        ConfiguracionContable.MetodoContable metodo = validar(input);
        var configuracion = configuracionActual();
        String anterior = configuracion.getMetodoContable().name();
        mapper.updateEntity(new ConfiguracionContableInput(metodo.name(), input.contabilizacionAutomatica(),
            input.permitirPeriodosCerrados()), configuracion);
        repository.save(configuracion);
        auditoria.registrar("ACCOUNTING_CONFIGURATION_UPDATED", "ConfiguracionContable", configuracion.getId(),
            "{\"metodoAnterior\":\"" + anterior + "\",\"metodoNuevo\":\"" + metodo.name() + "\"}");
        return mapper.toDto(configuracion);
    }

    private ConfiguracionContable configuracionActual() {
        var principal = TenantContext.principalActual();
        return repository.findByTenantIdAndEmpresaId(principal.tenantId(), EmpresaContext.requerirEmpresaId())
            .orElseGet(() -> {
                var moneda = monedas.findByEmpresaIdAndMonedaBaseTrue(principal.empresaId())
                    .orElseThrow(() -> new ReglaNegocioException(
                        "Define una moneda base antes de configurar la contabilidad."));
                return repository.save(new ConfiguracionContable(principal.tenantId(), principal.empresaId(),
                    moneda.getId(), moneda.getDecimales()));
            });
    }

    static ConfiguracionContable.MetodoContable validar(ConfiguracionContableInput input) {
        if (input == null || input.metodoContable() == null || input.metodoContable().isBlank()) {
            throw new ReglaNegocioException("El método contable es obligatorio.");
        }
        try {
            var metodo = ConfiguracionContable.MetodoContable.valueOf(
                input.metodoContable().trim().toUpperCase(Locale.ROOT));
            if (metodo != ConfiguracionContable.MetodoContable.DEVENGADO) {
                throw new ReglaNegocioException("El método efectivo todavía no está disponible.");
            }
            if (input.permitirPeriodosCerrados()) {
                throw new ReglaNegocioException("La contabilización en períodos cerrados no está habilitada.");
            }
            return metodo;
        } catch (IllegalArgumentException ex) {
            throw new ReglaNegocioException("El método contable seleccionado no es válido.");
        }
    }
}
