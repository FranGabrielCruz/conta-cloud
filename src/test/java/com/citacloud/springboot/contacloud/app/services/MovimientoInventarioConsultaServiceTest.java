package com.citacloud.springboot.contacloud.app.services;
import org.junit.jupiter.api.Test;import java.time.LocalDate;import static org.assertj.core.api.Assertions.*;
class MovimientoInventarioConsultaServiceTest {
 @Test void rechazaRangoInvertido(){assertThatThrownBy(()->MovimientoInventarioConsultaService.validate(0,10,LocalDate.of(2026,10,12),LocalDate.of(2026,10,1))).hasMessage("La fecha final no puede ser anterior a la fecha inicial.");}
 @Test void aceptaPaginacionBackendConfigurada(){assertThatCode(()->MovimientoInventarioConsultaService.validate(0,10,null,null)).doesNotThrowAnyException();assertThatCode(()->MovimientoInventarioConsultaService.validate(1,100,null,null)).doesNotThrowAnyException();}
 @Test void rechazaTamanoDePaginaNoPermitido(){assertThatThrownBy(()->MovimientoInventarioConsultaService.validate(0,1000,null,null)).hasMessage("Paginación inválida.");}
}
