package com.citacloud.springboot.contacloud.app.errors;

import java.util.Locale;
import java.util.UUID;
import org.slf4j.MDC;

public final class TraceContext {
    public static final String MDC_KEY = "traceId";
    public static final String HEADER = "X-Correlation-ID";

    private TraceContext() {}

    public static String currentOrCreate() {
        String current = MDC.get(MDC_KEY);
        if (current != null && current.matches("[A-Za-z0-9_-]{8,64}")) return current;
        String generated = UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        MDC.put(MDC_KEY, generated);
        return generated;
    }

    public static String shortId(String traceId) {
        return traceId == null ? "—" : traceId.substring(0, Math.min(8, traceId.length())).toUpperCase(Locale.ROOT);
    }
}
