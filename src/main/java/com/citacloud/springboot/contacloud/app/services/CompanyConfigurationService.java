package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.models.Empresa;
import com.citacloud.springboot.contacloud.app.dto.ConfiguracionEmpresaDto;
import com.citacloud.springboot.contacloud.app.mappers.ConfiguracionEmpresaMapper;
import com.citacloud.springboot.contacloud.app.models.DatosEmpresa;
import com.citacloud.springboot.contacloud.app.repositories.DatosEmpresaRepository;
import com.citacloud.springboot.contacloud.app.repositories.MonedaRepository;
import com.citacloud.springboot.contacloud.app.repositories.EmpresaRepository;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class CompanyConfigurationService {
    public static final int MAX_LOGO_BYTES = 2 * 1024 * 1024;
    private static final Logger log = LoggerFactory.getLogger(CompanyConfigurationService.class);
    private final EmpresaRepository empresas;
    private final LocalFileStorageService storage;
    private final AuditoriaService auditoria;
    private final DatosEmpresaRepository datosEmpresa;
    private final MonedaRepository monedas;
    private final ConfiguracionEmpresaMapper mapper;
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Set<String> ALLOWED_IMAGE_MIMES = Set.of(
        "image/png", "image/jpeg", "image/webp", "application/octet-stream");

    public CompanyConfigurationService(EmpresaRepository empresas, LocalFileStorageService storage,
                                       AuditoriaService auditoria, DatosEmpresaRepository datosEmpresa,
                                       MonedaRepository monedas, ConfiguracionEmpresaMapper mapper) {
        this.empresas = empresas;
        this.storage = storage;
        this.auditoria = auditoria;
        this.datosEmpresa = datosEmpresa;
        this.monedas = monedas;
        this.mapper = mapper;
    }

    @Transactional @PreAuthorize("hasAuthority('EMPRESA_VER')")
    public ConfiguracionEmpresaDto getConfiguration() {
        Empresa empresa = currentCompany();
        DatosEmpresa datos = datosEmpresa.findByEmpresaId(empresa.getId())
            .orElseGet(() -> datosEmpresa.save(new DatosEmpresa(empresa.getId(), empresa.getNombre())));
        var base = monedas.findByEmpresaIdAndMonedaBaseTrue(empresa.getId()).orElse(null);
        return mapper.toDto(empresa, datos, base);
    }

    @Transactional @PreAuthorize("hasAuthority('EMPRESA_EDITAR')")
    public ConfiguracionEmpresaDto updateConfiguration(ConfiguracionEmpresaDto dto) {
        if (dto == null || dto.nombreComercial() == null || dto.nombreComercial().trim().isEmpty())
            throw new ReglaNegocioException("El nombre comercial es obligatorio.");
        if (dto.nombreComercial().trim().length() > 150)
            throw new ReglaNegocioException("El nombre comercial es demasiado largo.");
        String rnc = normalizeRnc(dto.rnc());
        String phone = normalizePhone(dto.telefono());
        String email = normalizeEmail(dto.correo());
        Empresa empresa = empresas.findWithLockById(TenantContext.requerirEmpresaId())
            .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada"));
        DatosEmpresa datos = datosEmpresa.findByEmpresaId(empresa.getId())
            .orElseGet(() -> new DatosEmpresa(empresa.getId(), empresa.getNombre()));
        UUID previousBase = monedas.findByEmpresaIdAndMonedaBaseTrue(empresa.getId()).map(m -> m.getId()).orElse(null);
        if (dto.monedaBaseId() == null) throw new ReglaNegocioException("Selecciona la moneda base.");
        var selected = monedas.findByIdAndEmpresaId(dto.monedaBaseId(), empresa.getId())
            .filter(m -> m.isActivo()).orElseThrow(() -> new ReglaNegocioException("La moneda base no es válida."));
        if (!selected.getId().equals(previousBase)) {
            monedas.clearBase(empresa.getId());
            selected = monedas.findByIdAndEmpresaId(dto.monedaBaseId(), empresa.getId())
                .orElseThrow(() -> new ReglaNegocioException("La moneda base no es válida."));
            selected.setMonedaBase(true); monedas.save(selected);
            auditoria.registrar("BASE_CURRENCY_CHANGED", "Empresa", empresa.getId(), "{}");
        }
        mapper.update(empresa, datos, dto, rnc, phone, email);
        empresas.save(empresa); datosEmpresa.save(datos);
        auditoria.registrar("COMPANY_CONFIGURATION_UPDATED", "Empresa", empresa.getId(), "{}");
        return mapper.toDto(empresa, datos, selected);
    }

    public static String normalizeRnc(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String normalized = value.replaceAll("[\\s-]", "");
        if (!normalized.matches("\\d{9}")) throw new ReglaNegocioException("Introduzca un RNC válido.");
        return normalized;
    }

    public static String normalizePhone(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String normalized = value.replaceAll("[^0-9]", "");
        if (!normalized.matches("\\d{10}")) throw new ReglaNegocioException("Introduzca un teléfono válido.");
        return normalized;
    }

    public static String normalizeEmail(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(normalized).matches())
            throw new ReglaNegocioException("Introduzca un correo electrónico válido.");
        return normalized;
    }

    @Transactional @PreAuthorize("hasAuthority('EMPRESA_EDITAR')")
    public void replaceCompanyLogo(InputStream input, String originalName, String declaredMime) {
        byte[] bytes;
        try {
            bytes = input.readNBytes(MAX_LOGO_BYTES + 1);
        } catch (IOException ex) {
            throw new IllegalArgumentException("No fue posible leer el logo", ex);
        }
        String extension = validateLogo(originalName, declaredMime, bytes);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("La actualización del logo requiere una transacción");
        }
        Empresa empresa = currentCompany();
        String oldKey = empresa.getLogoObjectKey();
        String newKey = storage.store(logoDirectory(empresa), extension, bytes);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                if (oldKey != null && ownedByCompany(empresa, oldKey)) {
                    try { storage.delete(oldKey); }
                    catch (RuntimeException ex) { log.error("No se pudo eliminar el logo anterior de empresa {}", empresa.getId(), ex); }
                }
            }
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try { storage.delete(newKey); }
                    catch (RuntimeException ex) { log.error("No se pudo limpiar el logo nuevo tras rollback de empresa {}", empresa.getId(), ex); }
                }
            }
        });
        empresa.setLogoObjectKey(newKey);
        empresas.saveAndFlush(empresa);
        auditoria.registrar("COMPANY_LOGO_UPDATED", "Empresa", empresa.getId(), "{}");
    }

    @Transactional(readOnly = true) @PreAuthorize("isAuthenticated()")
    public Optional<Logo> currentLogo() {
        Empresa empresa = currentCompany();
        String key = empresa.getLogoObjectKey();
        if (key == null) return Optional.empty();
        if (!ownedByCompany(empresa, key)) {
            log.error("Object key del logo inválido para empresa {}", empresa.getId());
            return Optional.empty();
        }
        try {
            Optional<byte[]> bytes = storage.load(key, MAX_LOGO_BYTES);
            if (bytes.isEmpty()) log.error("Falta el archivo del logo de empresa {}", empresa.getId());
            return bytes.map(data -> new Logo(data, mimeFromKey(key)));
        } catch (RuntimeException ex) {
            log.error("No se pudo leer el logo de empresa {}", empresa.getId(), ex);
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true) @PreAuthorize("isAuthenticated()")
    public boolean currentLogoAvailable() {
        Empresa empresa = currentCompany();
        String key = empresa.getLogoObjectKey();
        if (key == null) return false;
        if (!ownedByCompany(empresa, key)) {
            log.error("Object key del logo inválido para empresa {}", empresa.getId());
            return false;
        }
        try {
            boolean available = storage.exists(key);
            if (!available) log.error("Falta el archivo del logo de empresa {}", empresa.getId());
            return available;
        } catch (RuntimeException ex) {
            log.error("No se pudo comprobar el logo de empresa {}", empresa.getId(), ex);
            return false;
        }
    }

    static String validateLogo(String name, String mime, byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_LOGO_BYTES) {
            throw new IllegalArgumentException("El logo debe tener entre 1 byte y 2 MB");
        }
        String lowerName = name == null ? "" : name.toLowerCase(Locale.ROOT);
        int dot = lowerName.lastIndexOf('.');
        String extension = dot >= 0 ? lowerName.substring(dot + 1) : "";
        if (!Set.of("png", "jpg", "jpeg", "webp").contains(extension)) {
            throw new IllegalArgumentException("Formato de logo no permitido");
        }
        String normalizedMime = mime == null ? "" : mime.trim().toLowerCase(Locale.ROOT);
        if (!normalizedMime.isEmpty() && !ALLOWED_IMAGE_MIMES.contains(normalizedMime)) {
            throw new IllegalArgumentException("Tipo MIME de logo no permitido");
        }
        String detected = detectMime(bytes);
        if (detected == null) throw new IllegalArgumentException("El contenido no corresponde a una imagen permitida");
        return expectedExtension(detected);
    }

    private static String detectMime(byte[] bytes) {
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N'
            && bytes[3] == 'G' && bytes[4] == 13 && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10) return "image/png";
        if (bytes.length >= 4 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
            && (bytes[2] & 0xff) == 0xff) return "image/jpeg";
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
            && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') return "image/webp";
        return null;
    }

    private static String expectedExtension(String mime) {
        return switch (mime) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            default -> "";
        };
    }

    private static String mimeFromKey(String key) {
        return key.endsWith(".png") ? "image/png" : key.endsWith(".webp") ? "image/webp" : "image/jpeg";
    }

    private static String logoDirectory(Empresa empresa) {
        return "tenants/" + empresa.getTenantId() + "/companies/" + empresa.getId() + "/logos";
    }

    private static boolean ownedByCompany(Empresa empresa, String key) {
        String prefix = logoDirectory(empresa) + "/logo-";
        if (!key.startsWith(prefix)) return false;
        String file = key.substring(prefix.length());
        return file.matches("[0-9a-fA-F-]{36}\\.(png|jpg|webp)")
            && isUuid(file.substring(0, 36));
    }

    private static boolean isUuid(String value) {
        try { UUID.fromString(value); return true; }
        catch (IllegalArgumentException ex) { return false; }
    }

    private Empresa currentCompany() {
        return empresas.findById(TenantContext.requerirEmpresaId())
            .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada"));
    }

    public record Logo(byte[] bytes, String mimeType) {}
}
