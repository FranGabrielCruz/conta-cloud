package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.models.Empresa;
import com.citacloud.springboot.contacloud.app.mappers.ConfiguracionEmpresaMapper;
import com.citacloud.springboot.contacloud.app.repositories.EmpresaRepository;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({CompanyConfigurationService.class, LocalFileStorageService.class, ConfiguracionEmpresaMapper.class,
    CompanyLogoPersistenceIntegrationTest.TestConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CompanyLogoPersistenceIntegrationTest {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1};
    @TempDir static Path storageDirectory;

    @DynamicPropertySource
    static void storagePath(DynamicPropertyRegistry registry) {
        registry.add("app.storage.local.base-path", storageDirectory::toString);
    }

    @Autowired EmpresaRepository empresas;
    @Autowired CompanyConfigurationService service;
    @Autowired LocalFileStorageService storage;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuditoriaService auditoria;

    @AfterEach void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        empresas.deleteAll();
    }

    @Test void persisteReemplazaYCompensaSiLaTransaccionFalla() throws IOException {
        UUID empresaId = UUID.randomUUID();
        jdbc.update("INSERT INTO empresas(id,tenant_id,codigo,nombre,zona_horaria,activo,creado_en,actualizado_en) " +
                "VALUES (?,?,?,?,?,TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
            empresaId, UUID.randomUUID(), "INT", "Integración", "America/Caracas");
        Empresa empresa = empresas.findById(empresaId).orElseThrow();
        authenticate(empresa.getId());

        service.replaceCompanyLogo(new ByteArrayInputStream(PNG), "primero.png", "image/png");
        String firstKey = empresas.findById(empresa.getId()).orElseThrow().getLogoObjectKey();
        assertTrue(storage.exists(firstKey));

        service.replaceCompanyLogo(new ByteArrayInputStream(PNG), "segundo.png", "image/png");
        String secondKey = empresas.findById(empresa.getId()).orElseThrow().getLogoObjectKey();
        assertNotEquals(firstKey, secondKey);
        assertFalse(storage.exists(firstKey));
        assertTrue(storage.exists(secondKey));
        assertArrayEquals(PNG, service.currentLogo().orElseThrow().bytes());

        doThrow(new IllegalStateException("Fallo de auditoría")).when(auditoria)
            .registrar("COMPANY_LOGO_UPDATED", "Empresa", empresaId, "{}");
        assertThrows(IllegalStateException.class,
            () -> service.replaceCompanyLogo(new ByteArrayInputStream(PNG), "fallido.png", "image/png"));
        assertEquals(secondKey, empresas.findById(empresaId).orElseThrow().getLogoObjectKey());
        assertTrue(storage.exists(secondKey));
        try (var files = Files.walk(storageDirectory)) {
            assertEquals(1, files.filter(Files::isRegularFile).count());
        }
    }

    @Test @WithMockUser(authorities = "EMPRESA_VER")
    void usuarioSinPermisoNoPuedeReemplazarElLogo() {
        assertThrows(AccessDeniedException.class,
            () -> service.replaceCompanyLogo(new ByteArrayInputStream(PNG), "logo.png", "image/png"));
    }

    private void authenticate(UUID empresaId) {
        var principal = new TenantPrincipal(UUID.randomUUID(), empresaId, "INT", "Usuario", "admin", "", true,
            Set.of("EMPRESA_VER", "EMPRESA_EDITAR"));
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class TestConfig {
        @Bean AuditoriaService auditoriaService() { return mock(AuditoriaService.class); }
    }
}
