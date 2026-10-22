package com.citacloud.springboot.contacloud.app.security;

import com.citacloud.springboot.contacloud.app.errors.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {
    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);
    private static final String GENERIC_MESSAGE = "Empresa, usuario o contraseña incorrectos";
    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration window;
    private final Duration blockDuration;
    private final Clock clock;

    @Autowired
    public LoginAttemptService(
        @Value("${contacloud.security.login.max-attempts:5}") int maxAttempts,
        @Value("${contacloud.security.login.window:PT15M}") Duration window,
        @Value("${contacloud.security.login.block-duration:PT15M}") Duration blockDuration) {
        this(maxAttempts, window, blockDuration, Clock.systemUTC());
    }

    LoginAttemptService(int maxAttempts, Duration window, Duration blockDuration, Clock clock) {
        this.maxAttempts = Math.max(2, maxAttempts);
        this.window = window;
        this.blockDuration = blockDuration;
        this.clock = clock;
    }

    public void verifyAllowed(String company, String username, String source) {
        String key = key(company, username, source);
        Attempt attempt = attempts.get(key);
        Instant now = clock.instant();
        if (attempt != null && attempt.blockedUntil() != null && attempt.blockedUntil().isAfter(now)) {
            log.warn("event=LOGIN_BLOCKED traceId={} identityHash={} source={}",
                TraceContext.currentOrCreate(), identityHash(company, username), safeSource(source));
            throw new LockedException(GENERIC_MESSAGE);
        }
        if (attempt != null && expired(attempt, now)) attempts.remove(key, attempt);
    }

    public void failed(String company, String username, String source) {
        Instant now = clock.instant();
        Attempt updated = attempts.compute(key(company, username, source), (key, current) -> {
            Attempt active = current == null || expired(current, now) ? new Attempt(0, now, null) : current;
            int count = active.failures() + 1;
            Instant blocked = count >= maxAttempts ? now.plus(blockDuration) : active.blockedUntil();
            return new Attempt(count, active.windowStartedAt(), blocked);
        });
        log.warn("event=LOGIN_FAILED traceId={} identityHash={} source={} attempts={} temporarilyBlocked={}",
            TraceContext.currentOrCreate(), identityHash(company, username), safeSource(source),
            updated.failures(), updated.blockedUntil() != null && updated.blockedUntil().isAfter(now));
    }

    public void succeeded(String company, String username, String source) {
        attempts.remove(key(company, username, source));
        log.info("event=LOGIN_SUCCEEDED traceId={} identityHash={} source={}",
            TraceContext.currentOrCreate(), identityHash(company, username), safeSource(source));
    }

    private boolean expired(Attempt attempt, Instant now) {
        return attempt.blockedUntil() == null && attempt.windowStartedAt().plus(window).isBefore(now)
            || attempt.blockedUntil() != null && !attempt.blockedUntil().isAfter(now);
    }

    private static String key(String company, String username, String source) {
        return normalize(company) + '|' + normalize(username) + '|' + safeSource(source);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String safeSource(String source) {
        if (source == null || source.isBlank()) return "unknown";
        String clean=source.replaceAll("[^0-9A-Fa-f:._-]", "");
        return clean.substring(0,Math.min(64,clean.length()));
    }

    private static String identityHash(String company, String username) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest((normalize(company) + '|' + normalize(username)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (Exception ex) {
            return "unavailable";
        }
    }

    private record Attempt(int failures, Instant windowStartedAt, Instant blockedUntil) {}
}
