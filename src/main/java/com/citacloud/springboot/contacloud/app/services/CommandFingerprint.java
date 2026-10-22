package com.citacloud.springboot.contacloud.app.services;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.temporal.TemporalAccessor;
import java.util.*;

/** Crea una representación canónica independiente de JSON y una huella SHA-256 estable. */
public final class CommandFingerprint {
    private CommandFingerprint() {}

    public static String sha256(Object value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible", exception);
        }
    }

    static String canonical(Object value) {
        if (value == null) return "null";
        if (value instanceof CharSequence text) return "s:" + escape(text.toString());
        if (value instanceof UUID || value instanceof Enum<?> || value instanceof TemporalAccessor)
            return value.getClass().getSimpleName() + ':' + value;
        if (value instanceof BigDecimal number) return "decimal:" + number.stripTrailingZeros().toPlainString();
        if (value instanceof Number || value instanceof Boolean) return value.getClass().getSimpleName() + ':' + value;
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                .sorted(Comparator.comparing(entry -> canonical(entry.getKey())))
                .map(entry -> canonical(entry.getKey()) + '=' + canonical(entry.getValue()))
                .reduce("map{", (left, right) -> left + right + ';') + '}';
        }
        if (value instanceof Set<?> set) {
            return set.stream().map(CommandFingerprint::canonical).sorted()
                .reduce("set[", (left, right) -> left + right + ';') + ']';
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(CommandFingerprint::canonical)
                .reduce("list[", (left, right) -> left + right + ';') + ']';
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            StringBuilder result = new StringBuilder("array[");
            for (int index = 0; index < length; index++) result.append(canonical(java.lang.reflect.Array.get(value, index))).append(';');
            return result.append(']').toString();
        }
        if (value.getClass().isRecord()) {
            StringBuilder result = new StringBuilder(value.getClass().getName()).append('{');
            try {
                for (RecordComponent component : value.getClass().getRecordComponents()) {
                    result.append(component.getName()).append('=').append(canonical(component.getAccessor().invoke(value))).append(';');
                }
            } catch (ReflectiveOperationException exception) {
                throw new IllegalArgumentException("No fue posible calcular la huella del comando", exception);
            }
            return result.append('}').toString();
        }
        throw new IllegalArgumentException("Tipo no soportado para huella idempotente: " + value.getClass().getName());
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace(";", "\\;").replace("=", "\\=");
    }
}

