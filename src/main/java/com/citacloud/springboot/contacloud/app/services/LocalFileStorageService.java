package com.citacloud.springboot.contacloud.app.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.UUID;

@Service
public class LocalFileStorageService {
    private final Path basePath;

    public LocalFileStorageService(@Value("${app.storage.local.base-path}") String basePath) {
        this.basePath = Path.of(basePath).toAbsolutePath().normalize();
    }

    public String store(String directory, String extension, byte[] bytes) {
        if (!extension.matches("png|jpg|webp")) throw new IllegalArgumentException("Extensión no permitida");
        String key = directory + "/logo-" + UUID.randomUUID() + "." + extension;
        Path target = resolveSafePath(key);
        try {
            Files.createDirectories(target.getParent());
            rejectSymlinks(target.getParent());
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return key;
        } catch (IOException ex) {
            throw new UncheckedIOException("No fue posible guardar el archivo local", ex);
        }
    }

    public Optional<byte[]> load(String key) {
        return load(key, Long.MAX_VALUE);
    }

    public Optional<byte[]> load(String key, long maxBytes) {
        Path path = resolveSafePath(key);
        try {
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("Archivo inválido");
            if (Files.size(path) > maxBytes) throw new IllegalArgumentException("Archivo demasiado grande");
            return Optional.of(Files.readAllBytes(path));
        } catch (IOException ex) {
            throw new UncheckedIOException("No fue posible leer el archivo local", ex);
        }
    }

    public boolean exists(String key) {
        return Files.isRegularFile(resolveSafePath(key), LinkOption.NOFOLLOW_LINKS);
    }

    public void delete(String key) {
        try {
            Files.deleteIfExists(resolveSafePath(key));
        } catch (IOException ex) {
            throw new UncheckedIOException("No fue posible eliminar el archivo local", ex);
        }
    }

    public Path resolveSafePath(String key) {
        if (key == null || !key.matches("[A-Za-z0-9_./-]+") || key.contains("..")
            || key.startsWith("/") || key.contains("//")) {
            throw new IllegalArgumentException("Ruta de archivo inválida");
        }
        Path resolved = basePath.resolve(key).normalize();
        if (!resolved.startsWith(basePath) || resolved.equals(basePath)) {
            throw new IllegalArgumentException("Ruta fuera del almacenamiento local");
        }
        rejectSymlinks(resolved);
        return resolved;
    }

    private void rejectSymlinks(Path path) {
        for (Path current = path; current != null && current.startsWith(basePath); current = current.getParent()) {
            if (Files.isSymbolicLink(current)) throw new IllegalArgumentException("Ruta simbólica no permitida");
        }
    }
}
