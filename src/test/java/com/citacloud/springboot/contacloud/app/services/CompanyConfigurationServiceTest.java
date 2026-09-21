package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.models.Empresa;
import com.citacloud.springboot.contacloud.app.repositories.EmpresaRepository;
import com.citacloud.springboot.contacloud.app.repositories.DatosEmpresaRepository;
import com.citacloud.springboot.contacloud.app.repositories.MonedaRepository;
import com.citacloud.springboot.contacloud.app.mappers.ConfiguracionEmpresaMapper;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyConfigurationServiceTest {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1};
    @TempDir Path temp;
    @Mock EmpresaRepository repository;
    @Mock AuditoriaService auditoria;
    @Mock DatosEmpresaRepository datosEmpresa;
    @Mock MonedaRepository monedas;
    private LocalFileStorageService storage;
    private CompanyConfigurationService service;
    private Empresa empresa;
    private UUID empresaId;

    @BeforeEach void setup() {
        storage = new LocalFileStorageService(temp.toString());
        service = new CompanyConfigurationService(repository, storage, auditoria, datosEmpresa, monedas,
            new ConfiguracionEmpresaMapper());
        empresaId = UUID.randomUUID();
        empresa = new Empresa("A", "Empresa A");
        ReflectionTestUtils.setField(empresa, "id", empresaId);
        var principal = new TenantPrincipal(UUID.randomUUID(), empresaId, "A", "Usuario", "admin", "", true,
            Set.of("EMPRESA_EDITAR", "EMPRESA_VER"));
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        lenient().when(repository.findById(empresaId)).thenReturn(Optional.of(empresa));
    }

    @AfterEach void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.clearSynchronization();
        SecurityContextHolder.clearContext();
    }

    @Test void reemplazaSoloDespuesDelCommitYConservaElAnteriorAnteError() {
        begin();
        service.replaceCompanyLogo(new ByteArrayInputStream(PNG), "logo.png", "image/png");
        String first = empresa.getLogoObjectKey();
        assertTrue(first.startsWith("tenants/" + empresa.getTenantId() + "/companies/" + empresaId + "/logos/"));
        assertTrue(storage.exists(first));
        complete(true);

        begin();
        service.replaceCompanyLogo(new ByteArrayInputStream(PNG), "otro.png", "image/png");
        String second = empresa.getLogoObjectKey();
        assertNotEquals(first, second);
        assertTrue(storage.exists(first));
        assertTrue(storage.exists(second));
        complete(true);
        assertFalse(storage.exists(first));
        assertTrue(storage.exists(second));

        begin();
        doThrow(new IllegalStateException("DB falló")).when(repository).saveAndFlush(any(Empresa.class));
        assertThrows(IllegalStateException.class,
            () -> service.replaceCompanyLogo(new ByteArrayInputStream(PNG), "fallo.png", "image/png"));
        String failed = empresa.getLogoObjectKey();
        complete(false);
        assertFalse(storage.exists(failed));
        assertTrue(storage.exists(second));
    }

    @Test void noLeeLogoDeOtraEmpresaAunqueLaClaveSeaManipulada() {
        UUID other = UUID.randomUUID();
        String foreign = "tenants/" + UUID.randomUUID() + "/companies/" + other + "/logos/logo-" + UUID.randomUUID() + ".png";
        empresa.setLogoObjectKey(foreign);
        assertTrue(service.currentLogo().isEmpty());
        assertFalse(service.currentLogoAvailable());
        verify(repository, atLeastOnce()).findById(empresaId);
        verify(repository, never()).findById(other);
    }

    @Test void archivoFaltanteDevuelveFallbackSinRomperLaPantalla() {
        empresa.setLogoObjectKey("tenants/" + empresa.getTenantId() + "/companies/" + empresaId
            + "/logos/logo-" + UUID.randomUUID() + ".png");
        assertTrue(service.currentLogo().isEmpty());
        assertFalse(service.currentLogoAvailable());
    }

    @Test void validaTamanoMimeExtensionYFirma() {
        assertEquals("png", CompanyConfigurationService.validateLogo("logo.png", "image/png", PNG));
        assertEquals("png", CompanyConfigurationService.validateLogo("foto.jpg", "image/jpeg", PNG));
        assertEquals("png", CompanyConfigurationService.validateLogo("foto.jpg", "application/octet-stream", PNG));
        assertThrows(IllegalArgumentException.class,
            () -> CompanyConfigurationService.validateLogo("logo.svg", "image/png", PNG));
        assertThrows(IllegalArgumentException.class,
            () -> CompanyConfigurationService.validateLogo("logo.png", "text/html", PNG));
        assertThrows(IllegalArgumentException.class,
            () -> CompanyConfigurationService.validateLogo("logo.png", "image/png", new byte[]{1, 2, 3}));
        assertThrows(IllegalArgumentException.class,
            () -> CompanyConfigurationService.validateLogo("logo.png", "image/png", new byte[CompanyConfigurationService.MAX_LOGO_BYTES + 1]));
    }

    @Test void normalizaYValidaRncTelefonoYCorreo() {
        assertEquals("101998871", CompanyConfigurationService.normalizeRnc("101-99887-1"));
        assertThrows(ReglaNegocioException.class, () -> CompanyConfigurationService.normalizeRnc("123"));
        assertEquals("8095551234", CompanyConfigurationService.normalizePhone("(809) 555-1234"));
        assertThrows(ReglaNegocioException.class, () -> CompanyConfigurationService.normalizePhone("809-12"));
        assertEquals("info@empresa.com", CompanyConfigurationService.normalizeEmail(" INFO@EMPRESA.COM "));
        assertThrows(ReglaNegocioException.class, () -> CompanyConfigurationService.normalizeEmail("correo-invalido"));
    }

    private void begin() { TransactionSynchronizationManager.initSynchronization(); }

    private void complete(boolean commit) {
        var synchronizations = TransactionSynchronizationManager.getSynchronizations();
        if (commit) synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(sync -> sync.afterCompletion(
            commit ? TransactionSynchronization.STATUS_COMMITTED : TransactionSynchronization.STATUS_ROLLED_BACK));
        TransactionSynchronizationManager.clearSynchronization();
    }
}
