package com.citacloud.springboot.contacloud.app.models;

import org.junit.jupiter.api.Test;import java.time.LocalDate;import java.util.UUID;import static org.assertj.core.api.Assertions.*;

class TransferenciaInventarioTest{
 @Test void directaPasaDeBorradorARecibida(){UUID user=UUID.randomUUID();var t=nueva(TipoTransferenciaInventario.DIRECT,user);t.confirmarDirecta(user);assertThat(t.getEstado()).isEqualTo(EstadoTransferenciaInventario.RECEIVED);assertThat(t.getDespachadaEn()).isNotNull();assertThat(t.getRecibidaEn()).isNotNull();}
 @Test void conTransitoExigeDespachoAntesDeRecepcion(){UUID user=UUID.randomUUID();var t=nueva(TipoTransferenciaInventario.IN_TRANSIT,user);assertThatThrownBy(()->t.recibir(user)).hasMessage("La transferencia no está en tránsito.");UUID transit=UUID.randomUUID();t.despachar(transit,user);assertThat(t.getEstado()).isEqualTo(EstadoTransferenciaInventario.IN_TRANSIT);assertThat(t.getAlmacenTransitoId()).isEqualTo(transit);t.recibir(user);assertThat(t.getEstado()).isEqualTo(EstadoTransferenciaInventario.RECEIVED);}
 @Test void transferenciaProcesadaNoPuedeEditar(){UUID user=UUID.randomUUID();var t=nueva(TipoTransferenciaInventario.DIRECT,user);t.confirmarDirecta(user);assertThatThrownBy(()->t.actualizar(LocalDate.now(),TipoTransferenciaInventario.DIRECT,UUID.randomUUID(),UUID.randomUUID(),null,null,user)).hasMessage("Solo se puede modificar una transferencia en borrador.");}
 private static TransferenciaInventario nueva(TipoTransferenciaInventario tipo,UUID user){return new TransferenciaInventario(UUID.randomUUID(),UUID.randomUUID(),"TRF-000001",LocalDate.now(),tipo,UUID.randomUUID(),UUID.randomUUID(),null,null,user);}
}
