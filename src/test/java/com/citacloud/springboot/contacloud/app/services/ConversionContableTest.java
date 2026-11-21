package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.dto.AsientoContableDto.Movimiento;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ConversionContableTest {
 @Test void conservaPartidaDobleYDistribuyeResiduoEnMayorPartidaSinLineaArtificial() {
  var lineas=List.of(l("1","0"),l("1","0"),l("1","0"),l("0","3"));
  var convertidas=ConversionContable.convertir(lineas,new BigDecimal("0.33333333"));
  assertThat(convertidas).hasSize(4);
  assertThat(convertidas.stream().map(Movimiento::debito).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("1.00");
  assertThat(convertidas.stream().map(Movimiento::credito).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("1.00");
  assertThat(convertidas.getFirst().debito()).isEqualByComparingTo("0.34");
 }
 @Test void rechazaTasaNoPositiva() {
  assertThatThrownBy(()->ConversionContable.convertir(List.of(l("1","0"),l("0","1")),BigDecimal.ZERO)).hasMessageContaining("mayor que cero");
 }
 private static Movimiento l(String d,String c){return new Movimiento(UUID.randomUUID(),"Prueba",new BigDecimal(d),new BigDecimal(c));}
}

