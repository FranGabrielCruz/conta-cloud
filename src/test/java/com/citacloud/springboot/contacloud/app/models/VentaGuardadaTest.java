package com.citacloud.springboot.contacloud.app.models;
import org.junit.jupiter.api.Test;import java.math.BigDecimal;import java.util.UUID;import static org.assertj.core.api.Assertions.*;
class VentaGuardadaTest {
 private final UUID tenant=UUID.randomUUID(),empresa=UUID.randomUUID(),usuario=UUID.randomUUID();
 @Test void recuperaYCompletaManteniendoLaIdentidad(){VentaGuardada v=venta();v.recuperar(usuario,UUID.randomUUID());assertThat(v.getEstado()).isEqualTo(EstadoVentaGuardada.IN_USE);UUID factura=UUID.randomUUID();v.completar(factura,usuario);assertThat(v.getEstado()).isEqualTo(EstadoVentaGuardada.COMPLETED);}
 @Test void unaVentaGuardadaPuedeDescartarseSinConvertirseEnFactura(){VentaGuardada v=venta();v.descartar(usuario);assertThat(v.getEstado()).isEqualTo(EstadoVentaGuardada.DISCARDED);}
 @Test void impideRecuperarDosVecesLaMismaVenta(){VentaGuardada v=venta();v.recuperar(usuario,UUID.randomUUID());assertThatThrownBy(()->v.recuperar(UUID.randomUUID(),UUID.randomUUID())).isInstanceOf(IllegalStateException.class);}
 @Test void liberarRecuperacionConservaLaVentaGuardadaDisponible(){VentaGuardada v=venta();v.recuperar(usuario,UUID.randomUUID());v.liberarRecuperacion(usuario);assertThat(v.getEstado()).isEqualTo(EstadoVentaGuardada.SAVED);assertThat(v.getRecuperadaPor()).isNull();assertThat(v.getTerminalRecuperacionId()).isNull();assertThat(v.getCantidadItems()).isEqualTo(1);}
 private VentaGuardada venta(){VentaGuardada v=new VentaGuardada(tenant,empresa,"VGU-000001",UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),usuario);v.actualizar(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),null,"Consumidor Final",null,UUID.randomUUID(),"DOP",null,null,new BigDecimal("100"),BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("18"),new BigDecimal("118"),1,usuario);return v;}
}
