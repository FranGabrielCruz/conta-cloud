package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.models.TipoIdentificacionCliente;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ClienteServiceTest {
    @Test void normalizaYFormateaRncDominicano(){
        String normal=ClienteService.normalizeIdentification("1-31-45678-9",TipoIdentificacionCliente.RNC);
        assertThat(normal).isEqualTo("131456789");
        assertThat(ClienteService.formatIdentification(normal,TipoIdentificacionCliente.RNC)).isEqualTo("131-45678-9");
    }

    @Test void normalizaYFormateaCedulaDominicana(){
        String normal=ClienteService.normalizeIdentification("001-1234567-8",TipoIdentificacionCliente.CEDULA);
        assertThat(normal).isEqualTo("00112345678");
        assertThat(ClienteService.formatIdentification(normal,TipoIdentificacionCliente.CEDULA)).isEqualTo("001-1234567-8");
    }

    @Test void conservaIdentificacionExtranjeraNormalizadaSinFormatoDominicano(){
        String normal=ClienteService.normalizeIdentification("US-TAX-ABC-12",TipoIdentificacionCliente.IDENTIFICACION_EXTRANJERA);
        assertThat(normal).isEqualTo("USTAXABC12");
        assertThat(ClienteService.formatIdentification(normal,TipoIdentificacionCliente.IDENTIFICACION_EXTRANJERA)).isEqualTo("USTAXABC12");
    }

    @Test void rechazaLongitudesInvalidasDeRncYCedula(){
        assertThatThrownBy(()->ClienteService.normalizeIdentification("123",TipoIdentificacionCliente.RNC))
            .hasMessage("El RNC debe contener 9 dígitos.");
        assertThatThrownBy(()->ClienteService.normalizeIdentification("001123",TipoIdentificacionCliente.CEDULA))
            .hasMessage("La cédula debe contener 11 dígitos.");
    }

    @Test void normalizaTelefonoDominicanoEInternacional(){
        assertThat(ClienteService.phone("809-555-1234")).isEqualTo("8095551234");
        assertThat(ClienteService.phone("+1 305 555 1234")).isEqualTo("+13055551234");
        assertThatThrownBy(()->ClienteService.phone("123")).hasMessage("El teléfono no tiene un formato válido.");
    }
}
