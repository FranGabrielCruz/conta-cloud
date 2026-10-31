package com.citacloud.springboot.contacloud.app.services;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.security.SecureRandom;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PrintAgentTokenServiceTest {
    @Test void generaCodigoCriptograficoDeSeisDigitosMedianteAbstraccionTesteable(){SecureRandom random=mock(SecureRandom.class);when(random.nextInt(1_000_000)).thenReturn(847291);var service=new PrintAgentTokenService(new byte[32],random);assertThat(service.nuevoCodigoVinculacion()).isEqualTo("847291");}
    @Test void tokenTieneAltaEntropiaTenantYHuellasSeparadas(){SecureRandom random=mock(SecureRandom.class);doAnswer(inv->{byte[] target=inv.getArgument(0);java.util.Arrays.fill(target,(byte)7);return null;}).when(random).nextBytes(any(byte[].class));var service=new PrintAgentTokenService(new byte[32],random);UUID tenant=UUID.randomUUID();String token=service.nuevoToken(tenant);assertThat(service.tenantDelToken(token)).isEqualTo(tenant);assertThat(token).doesNotContain("=");assertThat(service.hashAcceso(token)).hasSize(64).isNotEqualTo(service.hashRenovacion(token));assertThat(service.hashCodigo("847291")).doesNotContain("847291");}
}
