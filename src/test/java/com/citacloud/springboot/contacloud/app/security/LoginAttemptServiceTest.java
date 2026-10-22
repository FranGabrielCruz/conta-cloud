package com.citacloud.springboot.contacloud.app.security;

import com.citacloud.springboot.contacloud.app.multitenancy.DirectoryGateway;
import com.citacloud.springboot.contacloud.app.multitenancy.LoginAttemptState;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.LockedException;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
    @Test void usaElDirectorioCompartidoCuandoEstaDisponible(){
        DirectoryGateway directory=mock(DirectoryGateway.class);
        Instant now=Instant.now();
        when(directory.findLoginAttempt(anyString())).thenReturn(java.util.Optional.of(
            new LoginAttemptState(5,now.minusSeconds(30),now.plusSeconds(300))));
        var service=new LoginAttemptService(directory,5,Duration.ofMinutes(10),Duration.ofMinutes(15));
        assertThrows(LockedException.class,()->service.verifyAllowed("DEMO","admin","10.0.0.8"));
        service.succeeded("DEMO","admin","10.0.0.8");
        verify(directory).clearLoginAttempt(anyString());
    }
}
