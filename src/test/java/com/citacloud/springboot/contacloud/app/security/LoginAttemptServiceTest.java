package com.citacloud.springboot.contacloud.app.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.LockedException;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptServiceTest {
    @Test void bloqueaTemporalmentePorEmpresaUsuarioYOrigen(){
        var service=new LoginAttemptService(3,Duration.ofMinutes(10),Duration.ofMinutes(15),
            Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"),ZoneOffset.UTC));
        service.failed("DEMO","admin","127.0.0.1");
        service.failed("DEMO","admin","127.0.0.1");
        service.failed("DEMO","admin","127.0.0.1");
        assertThrows(LockedException.class,()->service.verifyAllowed("DEMO","admin","127.0.0.1"));
        assertDoesNotThrow(()->service.verifyAllowed("OTRA","admin","127.0.0.1"));
    }
    @Test void unInicioExitosoLimpiaElContador(){
        var service=new LoginAttemptService(2,Duration.ofMinutes(10),Duration.ofMinutes(15),Clock.systemUTC());
        service.failed("DEMO","admin","127.0.0.1");
        service.succeeded("DEMO","admin","127.0.0.1");
        assertDoesNotThrow(()->service.verifyAllowed("DEMO","admin","127.0.0.1"));
    }
}
