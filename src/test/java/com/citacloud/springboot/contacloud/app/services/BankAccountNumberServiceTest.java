package com.citacloud.springboot.contacloud.app.services;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class BankAccountNumberServiceTest {
    private final BankAccountNumberService service = new BankAccountNumberService(
        "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8));

    @Test
    void normalizaProtegeYRecuperaNumero() {
        String normalized = service.normalize(" 1234-5678 90 ");
        String encrypted = service.encrypt(normalized);

        assertThat(normalized).isEqualTo("1234567890");
        assertThat(encrypted).doesNotContain(normalized);
        assertThat(service.decrypt(encrypted)).isEqualTo(normalized);
        assertThat(service.last4(normalized)).isEqualTo("7890");
        assertThat(service.mask("7890")).isEqualTo("•••• 7890");
    }

    @Test
    void cifradoEsNoDeterministicoYFingerprintEsEstable() {
        String normalized = service.normalize("AB-123456");
        assertThat(service.encrypt(normalized)).isNotEqualTo(service.encrypt(normalized));
        assertThat(service.fingerprint(normalized)).isEqualTo(service.fingerprint("AB123456"));
    }

    @Test
    void rechazaVacioCaracteresInvalidosYLongitudInvalida() {
        assertThatThrownBy(() -> service.normalize("  ")).hasMessage("El número de cuenta es obligatorio.");
        assertThatThrownBy(() -> service.normalize("1234/5678"))
            .hasMessage("El número de cuenta contiene caracteres no permitidos.");
        assertThatThrownBy(() -> service.normalize("123"))
            .hasMessage("El número de cuenta debe tener entre 4 y 64 caracteres.");
    }
}
