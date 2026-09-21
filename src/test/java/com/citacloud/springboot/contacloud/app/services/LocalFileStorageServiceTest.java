package com.citacloud.springboot.contacloud.app.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalFileStorageServiceTest {
    @TempDir Path temp;

    @Test void guardaLeeEliminaYGeneraNombresUnicos() {
        var storage = new LocalFileStorageService(temp.toString());
        byte[] data = {1, 2, 3};
        String first = storage.store("tenants/t/companies/e/logos", "png", data);
        String second = storage.store("tenants/t/companies/e/logos", "png", data);
        assertNotEquals(first, second);
        assertArrayEquals(data, storage.load(first).orElseThrow());
        assertTrue(storage.exists(first));
        storage.delete(first);
        assertFalse(storage.exists(first));
        assertTrue(storage.load(first).isEmpty());
        assertTrue(storage.exists(second));
    }

    @Test void bloqueaTraversalRutasAbsolutasYExtensiones() {
        var storage = new LocalFileStorageService(temp.toString());
        assertThrows(IllegalArgumentException.class, () -> storage.resolveSafePath("../../secret"));
        assertThrows(IllegalArgumentException.class, () -> storage.resolveSafePath("/etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> storage.resolveSafePath("C:\\Windows\\secret"));
        assertThrows(IllegalArgumentException.class, () -> storage.store("../fuera", "png", new byte[]{1}));
        assertThrows(IllegalArgumentException.class, () -> storage.store("safe", "svg", new byte[]{1}));
    }
}
