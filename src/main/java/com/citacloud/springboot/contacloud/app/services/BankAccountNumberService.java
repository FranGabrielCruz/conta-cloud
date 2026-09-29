package com.citacloud.springboot.contacloud.app.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.text.Normalizer;
import java.util.*;

@Service
public class BankAccountNumberService {
    private static final String VERSION = "v1";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int MIN_LENGTH = 4;
    private static final int MAX_LENGTH = 64;
    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec fingerprintKey;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public BankAccountNumberService(
        @Value("${contacloud.security.bank-account-key:}") String configuredKey,
        @Value("${app.storage.local.base-path:./data/contacloud}") String storagePath) {
        byte[] master = configuredKey == null || configuredKey.isBlank()
            ? loadOrCreateLocalKey(Path.of(storagePath).resolve(".bank-account-key"))
            : decodeConfiguredKey(configuredKey.trim());
        encryptionKey = new SecretKeySpec(derive(master, "encryption"), "AES");
        fingerprintKey = new SecretKeySpec(derive(master, "fingerprint"), "HmacSHA256");
    }

    BankAccountNumberService(byte[] masterKey) {
        encryptionKey = new SecretKeySpec(derive(masterKey, "encryption"), "AES");
        fingerprintKey = new SecretKeySpec(derive(masterKey, "fingerprint"), "HmacSHA256");
    }

    /** Política central: se admiten letras y dígitos; espacios y guiones son separadores descartables. */
    public String normalize(String raw) {
        String value = raw == null ? "" : Normalizer.normalize(raw.trim(), Normalizer.Form.NFKC);
        if (value.isEmpty()) throw new ReglaNegocioException("El número de cuenta es obligatorio.");
        if (!value.matches("[\\p{L}\\p{N}\\s-]+"))
            throw new ReglaNegocioException("El número de cuenta contiene caracteres no permitidos.");
        String normalized = value.replaceAll("[\\s-]+", "").toUpperCase(Locale.ROOT);
        if (normalized.length() < MIN_LENGTH || normalized.length() > MAX_LENGTH)
            throw new ReglaNegocioException("El número de cuenta debe tener entre 4 y 64 caracteres.");
        return normalized;
    }

    public String encrypt(String normalized) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return VERSION + ":" + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No fue posible proteger el número de cuenta.", ex);
        }
    }

    public String decrypt(String protectedValue) {
        try {
            if (protectedValue == null || !protectedValue.startsWith(VERSION + ":"))
                throw new GeneralSecurityException("Formato protegido inválido");
            byte[] payload = Base64.getDecoder().decode(protectedValue.substring(VERSION.length() + 1));
            if (payload.length <= IV_BYTES) throw new GeneralSecurityException("Contenido protegido inválido");
            byte[] iv = Arrays.copyOfRange(payload, 0, IV_BYTES);
            byte[] encrypted = Arrays.copyOfRange(payload, IV_BYTES, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("No fue posible recuperar el número de cuenta protegido.", ex);
        }
    }

    public String fingerprint(String normalized) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(fingerprintKey);
            return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No fue posible validar el número de cuenta.", ex);
        }
    }

    public String last4(String normalized) {
        return normalized.substring(Math.max(0, normalized.length() - 4));
    }

    public String mask(String last4) { return "•••• " + last4; }

    private static byte[] decodeConfiguredKey(String value) {
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            if (decoded.length < 32) throw new IllegalArgumentException();
            return decoded;
        } catch (IllegalArgumentException ex) {
            if (value.length() < 32)
                throw new IllegalStateException("BANK_ACCOUNT_DATA_KEY debe contener al menos 32 caracteres o Base64 de 32 bytes.");
            return value.getBytes(StandardCharsets.UTF_8);
        }
    }

    private static byte[] loadOrCreateLocalKey(Path path) {
        try {
            if (Files.exists(path)) return Base64.getDecoder().decode(Files.readString(path).trim());
            Files.createDirectories(path.getParent());
            byte[] key = new byte[32];
            new SecureRandom().nextBytes(key);
            try {
                Files.writeString(path, Base64.getEncoder().encodeToString(key),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (FileAlreadyExistsException race) {
                return Base64.getDecoder().decode(Files.readString(path).trim());
            }
            return key;
        } catch (IOException | IllegalArgumentException ex) {
            throw new IllegalStateException("No fue posible cargar la clave de protección de cuentas bancarias.", ex);
        }
    }

    private static byte[] derive(byte[] master, String purpose) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(master, "HmacSHA256"));
            return mac.doFinal(("contacloud-bank-account-" + purpose + "-v1").getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No fue posible derivar la clave de protección.", ex);
        }
    }
}
