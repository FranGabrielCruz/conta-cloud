package com.citacloud.springboot.contacloud.app.views;

import com.vaadin.flow.spring.security.AuthenticationContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InactivityGuardTest {

    @Test
    void permaneceActivoParaAceptarLasLlamadasDelTemporizador() {
        InactivityGuard guard = new InactivityGuard(mock(AuthenticationContext.class),
                Duration.ofMinutes(15));

        assertThat(guard.isVisible()).isTrue();
        assertThat(guard.getClassNames()).contains("cc-inactivity-guard");
    }

    @Test
    void expiracionEjecutaElLogoutExistente() {
        AuthenticationContext authentication = mock(AuthenticationContext.class);
        InactivityGuard guard = new InactivityGuard(authentication,
                Duration.ofMinutes(15));

        guard.expirar();

        verify(authentication).logout();
    }

    @Test
    void rechazaUnaConfiguracionInvalida() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                InactivityGuard.validarConfiguracion(Duration.ZERO));
        assertThatIllegalArgumentException().isThrownBy(() ->
                InactivityGuard.validarConfiguracion(null));
    }

    @Test
    void noConservaAvisoInteractivoNiPermiteRevivirUnaSesionExpirada() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/citacloud/springboot/contacloud/app/views/InactivityGuard.java"));
        String properties = Files.readString(Path.of("src/main/resources/application.properties"));
        String login = Files.readString(Path.of(
                "src/main/java/com/citacloud/springboot/contacloud/app/views/LoginView.java"));

        assertThat(source).contains("let expired = false", "if (expired) return",
                "host.$server.expirar()", "cc-session-expiring");
        assertThat(source.split("setTimeout", -1)).hasSize(2);
        assertThat(source).doesNotContain("mostrarAdvertencia", "ConfirmDialog",
                "Continuar sesión", "warningTimer");
        assertThat(properties).doesNotContain("session.warning-after", "SESSION_WARNING_AFTER");
        assertThat(login).contains("Tu sesión se cerró por inactividad.",
                "sessionStorage.removeItem");
    }
}
