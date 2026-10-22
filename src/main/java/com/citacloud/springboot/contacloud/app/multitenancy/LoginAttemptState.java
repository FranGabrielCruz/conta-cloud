package com.citacloud.springboot.contacloud.app.multitenancy;

import java.time.Instant;

public record LoginAttemptState(int failures, Instant windowStartedAt, Instant blockedUntil) {}
