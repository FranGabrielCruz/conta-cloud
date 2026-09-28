package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ConfiguracionFiscalValidationTest {
    @Test void impuestoExigeNombreYTasaValida(){
        assertThrows(ReglaNegocioException.class,()->ImpuestoFiscalService.validar(new ImpuestoFiscalInput(" ",BigDecimal.TEN,null)));
        assertThrows(ReglaNegocioException.class,()->ImpuestoFiscalService.validar(new ImpuestoFiscalInput("ITBIS",null,null)));
        assertThrows(ReglaNegocioException.class,()->ImpuestoFiscalService.validar(new ImpuestoFiscalInput("ITBIS",new BigDecimal("100.01"),null)));
        assertDoesNotThrow(()->ImpuestoFiscalService.validar(new ImpuestoFiscalInput("Exento",BigDecimal.ZERO,null)));
    }

    @Test void comprobanteNormalizaCodigo(){
        var normalizado=ComprobanteFiscalService.validar(new ComprobanteFiscalInput(" cf ","Crédito fiscal"," b01 ",null));
        assertEquals("CF",normalizado.codigo());
        assertEquals("B01",normalizado.prefijo());
        assertThrows(ReglaNegocioException.class,()->ComprobanteFiscalService.validar(new ComprobanteFiscalInput("B 01","Crédito","B01",null)));
        assertThrows(ReglaNegocioException.class,()->ComprobanteFiscalService.validar(new ComprobanteFiscalInput("B01","Crédito"," ",null)));
    }

    @Test void secuenciaValidaRangoYPrefijo(){
        UUID comprobante=UUID.randomUUID();
        assertDoesNotThrow(()->SecuenciaFiscalAdminService.validar(new SecuenciaFiscalInput(comprobante,1,99999999L)));
        assertThrows(ReglaNegocioException.class,()->SecuenciaFiscalAdminService.validar(new SecuenciaFiscalInput(comprobante,100,99L)));
        assertThrows(ReglaNegocioException.class,()->SecuenciaFiscalAdminService.validar(new SecuenciaFiscalInput(null,1,10L)));
    }
}
