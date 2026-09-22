package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import com.citacloud.springboot.contacloud.app.security.ModuleAuthorization;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({CompanyConfigurationService.class, CompanyCurrencyService.class, CurrencyCatalogService.class,
    BranchService.class, LocalFileStorageService.class, ConfiguracionEmpresaMapper.class,
    MonedaMapper.class, SucursalMapper.class, ConfigurationPersistenceIntegrationTest.TestConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ConfigurationPersistenceIntegrationTest {
    @TempDir static Path storageDirectory;
    @DynamicPropertySource static void storage(DynamicPropertyRegistry registry) {
        registry.add("app.storage.local.base-path", storageDirectory::toString);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired EmpresaRepository empresas;
    @Autowired DatosEmpresaRepository datos;
    @Autowired CompanyConfigurationService configuration;
    @Autowired CompanyCurrencyService currencies;
    @Autowired BranchService branches;

    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM sucursales"); jdbc.update("DELETE FROM monedas");
        jdbc.update("DELETE FROM datos_empresa"); jdbc.update("DELETE FROM empresas");
        jdbc.update("DELETE FROM catalogo_monedas");
    }

    @Test void configuraEmpresaSucursalesMonedasPaginacionYAislamiento() {
        UUID empresaA = insertCompany("A", "Empresa A");
        UUID empresaB = insertCompany("B", "Empresa B");
        insertCatalog(); authenticate(empresaA);

        MonedaDto dop = toDto(currencies.ensureDop(empresaA));
        assertEquals(dop.id(), currencies.ensureDop(empresaA).getId());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM monedas WHERE empresa_id=? AND codigo_iso='DOP'", Long.class, empresaA));
        var initial = configuration.getConfiguration();
        var saved = configuration.updateConfiguration(new ConfiguracionEmpresaDto(" Comercial ABC ", "ABC SRL",
            "101-99887-1", "(809) 555-1234", " INFO@ABC.COM ", dop.id(), " Santo Domingo "));
        assertEquals("Comercial ABC", saved.nombreComercial());
        assertEquals("101998871", saved.rnc()); assertEquals("8095551234", saved.telefono());
        assertEquals("info@abc.com", saved.correo()); assertEquals("Comercial ABC", empresas.findById(empresaA).orElseThrow().getNombre());
        assertEquals("Santo Domingo", datos.findByEmpresaId(empresaA).orElseThrow().getDireccion());

        var principal = branches.create(new SucursalDto(null, "Sucursal Principal", "Centro", "8095550000", false, true));
        var norte = branches.create(new SucursalDto(null, "Sucursal Norte", "Santiago", "8095551111", false, true));
        assertTrue(principal.principal()); assertFalse(norte.principal());
        String generatedCode = jdbc.queryForObject("SELECT codigo FROM sucursales WHERE id=?", String.class, norte.id());
        assertNotNull(generatedCode); assertTrue(generatedCode.startsWith("SUC-")); assertEquals(30, generatedCode.length());
        assertEquals(2, branches.search("", null, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(1, branches.search("Santiago", true, PageRequest.of(0, 10)).getTotalElements());
        branches.disable(norte.id());
        assertEquals(1, branches.search("Sucursal", false, PageRequest.of(0, 10)).getTotalElements());

        MonedaDto usd = currencies.addFromCatalog("USD");
        assertThrows(ReglaNegocioException.class, () -> currencies.addFromCatalog("USD"));
        configuration.updateConfiguration(new ConfiguracionEmpresaDto("Comercial ABC", "ABC SRL", "101998871",
            "8095551234", "info@abc.com", usd.id(), "Santo Domingo"));
        assertThrows(ReglaNegocioException.class, () -> currencies.disable(usd.id()));
        currencies.disable(dop.id());
        assertEquals(1, currencies.search("dólar", true, PageRequest.of(0, 10)).getTotalElements());
        MonedaDto custom = currencies.create(new NuevaMonedaDto("xcr", "Crédito interno", "CR", (short) 4));
        assertEquals("XCR", custom.codigoIso());
        assertEquals("Crédito interno", custom.nombre()); assertEquals("CR", custom.simbolo()); assertEquals(4, custom.decimales());
        assertThrows(ReglaNegocioException.class,
            () -> currencies.create(new NuevaMonedaDto("XCR", "Duplicada", "XD", (short) 2)));
        assertThrows(ReglaNegocioException.class,
            () -> currencies.create(new NuevaMonedaDto("US", "Código inválido", "$", (short) 2)));
        assertEquals(3, currencies.search("", null, PageRequest.of(0, 10)).getTotalElements());

        UUID branchB = UUID.randomUUID(); UUID currencyB = UUID.randomUUID();
        jdbc.update("INSERT INTO sucursales(id,empresa_id,codigo,nombre,principal,activo) VALUES (?,?,?,?,FALSE,TRUE)",
            branchB, empresaB, "B-1", "Sucursal B");
        jdbc.update("INSERT INTO monedas(id,empresa_id,codigo_iso,nombre,simbolo,decimales,moneda_base,activo) VALUES (?,?,?,?,?,2,FALSE,TRUE)",
            currencyB, empresaB, "EUR", "Euro", "€");
        assertThrows(RecursoNoEncontradoException.class, () -> branches.get(branchB));
        assertThrows(RecursoNoEncontradoException.class, () -> currencies.get(currencyB));
    }

    private UUID insertCompany(String code, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO empresas(id,tenant_id,codigo,nombre,zona_horaria,activo,creado_en,actualizado_en) " +
            "VALUES (?,?,?,?,?,TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id, UUID.randomUUID(), code, name, "America/Caracas");
        return id;
    }
    private void insertCatalog() {
        jdbc.update("INSERT INTO catalogo_monedas(codigo_iso,nombre,simbolo,decimales,activo) VALUES ('DOP','Peso dominicano','RD$',2,TRUE)");
        jdbc.update("INSERT INTO catalogo_monedas(codigo_iso,nombre,simbolo,decimales,activo) VALUES ('USD','Dólar estadounidense','US$',2,TRUE)");
    }
    private void authenticate(UUID empresaId) {
        Set<String> permissions = Set.of("EMPRESA_VER","EMPRESA_EDITAR","SUCURSAL_VER","SUCURSAL_CREAR",
            "SUCURSAL_EDITAR","SUCURSAL_DESACTIVAR","MONEDA_VER","MONEDA_CREAR","MONEDA_EDITAR","MONEDA_DESACTIVAR");
        var principal = new TenantPrincipal(UUID.randomUUID(), empresaId, "A", "Usuario", "admin", "", true, permissions);
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
    private MonedaDto toDto(com.citacloud.springboot.contacloud.app.models.Moneda m) {
        return new MonedaDto(m.getId(), m.getCodigoIso(), m.getNombre(), m.getSimbolo(), m.getDecimales(), m.isMonedaBase(), m.isActivo());
    }

    @TestConfiguration @EnableMethodSecurity
    static class TestConfig {
        @Bean AuditoriaService auditoriaService() { return mock(AuditoriaService.class); }
        @Bean ModuleAuthorization moduleAuthorization() {
            EmpresaModuloService modules = mock(EmpresaModuloService.class);
            when(modules.habilitado(anyString())).thenReturn(true);
            return new ModuleAuthorization(modules);
        }
    }
}
