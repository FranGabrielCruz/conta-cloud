package com.citacloud.springboot.contacloud.app.views.components;

/** Detecta dos pulsaciones elegibles de Enter dentro de una ventana breve. */
public final class DoubleEnterDetector {
    public static final long DEFAULT_WINDOW_MILLIS = 450L;

    private final long windowMillis;
    private long previousMillis = -1L;

    public DoubleEnterDetector() {
        this(DEFAULT_WINDOW_MILLIS);
    }

    public DoubleEnterDetector(long windowMillis) {
        if (windowMillis <= 0) throw new IllegalArgumentException("La ventana debe ser mayor que cero.");
        this.windowMillis = windowMillis;
    }

    public boolean register(long timestampMillis, boolean eligible) {
        if (!eligible) {
            reset();
            return false;
        }
        boolean detected = previousMillis >= 0
                && timestampMillis >= previousMillis
                && timestampMillis - previousMillis <= windowMillis;
        previousMillis = detected ? -1L : timestampMillis;
        return detected;
    }

    public void reset() {
        previousMillis = -1L;
    }
}
