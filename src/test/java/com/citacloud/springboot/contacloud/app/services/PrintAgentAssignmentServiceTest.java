package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class PrintAgentAssignmentServiceTest {
 final CashRegisterRepository cajas=mock(CashRegisterRepository.class);final AgenteImpresionRepository agentes=mock(AgenteImpresionRepository.class);
 final PrintAgentAssignmentService service=new PrintAgentAssignmentService(cajas,agentes);
 final UUID tenant=UUID.randomUUID(),empresa=UUID.randomUUID(),sucursal=UUID.randomUUID(),agent=UUID.randomUUID();
 @ParameterizedTest @ValueSource(ints={1,2,10}) void aceptaUnaVariasYDiezCajasDeLaMismaSucursal(int cantidad){List<UUID> ids=new ArrayList<>();for(int i=0;i<cantidad;i++){UUID id=UUID.randomUUID();ids.add(id);caja(id);}assertThat(service.validar(tenant,empresa,sucursal,agent,ids,List.of(),true)).hasSize(cantidad);}
 @Test void exigeAlMenosUnaCaja(){assertThatThrownBy(()->service.validar(tenant,empresa,sucursal,agent,List.of(),List.of(),true)).isInstanceOf(ReglaNegocioException.class).hasMessage("Seleccione al menos una caja.");verifyNoInteractions(cajas,agentes);}
 @Test void rechazaCajasDuplicadasAntesDeConsultar(){UUID id=UUID.randomUUID();assertThatThrownBy(()->service.validar(tenant,empresa,sucursal,agent,List.of(id,id),List.of(),true)).isInstanceOf(ReglaNegocioException.class);verifyNoInteractions(cajas,agentes);}
 @ParameterizedTest @ValueSource(strings={"tenant","empresa","sucursal","inactiva","inexistente"}) void rechazaAsignacionesIncompatibles(String caso){UUID id=UUID.randomUUID();Caja c=caja(id);switch(caso){case "tenant"->when(c.getTenantId()).thenReturn(UUID.randomUUID());case "empresa"->when(c.getEmpresaId()).thenReturn(UUID.randomUUID());case "sucursal"->when(c.getSucursalId()).thenReturn(UUID.randomUUID());case "inactiva"->when(c.isActivo()).thenReturn(false);default->when(cajas.bloquearParaOperacion(id,tenant,empresa)).thenReturn(Optional.empty());}assertThatThrownBy(()->service.validar(tenant,empresa,sucursal,agent,List.of(id),List.of(),true)).isInstanceOf(ReglaNegocioException.class);}
 @Test void rechazaDestinoActivoAmbiguoConNombreDeCaja(){UUID id=UUID.randomUUID();caja(id);when(agentes.otrosDestinosActivos(tenant,empresa,id,agent)).thenReturn(1L);assertThatThrownBy(()->service.validar(tenant,empresa,sucursal,agent,List.of(id),List.of(),true)).hasMessage("La caja Caja 01 ya está asignada a otro agente de impresión.");}
 @Test void bloqueaCajasNuevasYAnterioresEnOrdenDeterminista(){UUID menor=new UUID(0,1),mayor=new UUID(0,2),anterior=new UUID(0,3);caja(menor);caja(mayor);caja(anterior);service.validar(tenant,empresa,sucursal,agent,List.of(mayor,menor),List.of(anterior),true);var order=inOrder(cajas);order.verify(cajas).bloquearParaOperacion(menor,tenant,empresa);order.verify(cajas).bloquearParaOperacion(mayor,tenant,empresa);order.verify(cajas).bloquearParaOperacion(anterior,tenant,empresa);}
 private Caja caja(UUID id){Caja c=mock(Caja.class);when(c.getId()).thenReturn(id);when(c.getTenantId()).thenReturn(tenant);when(c.getEmpresaId()).thenReturn(empresa);when(c.getSucursalId()).thenReturn(sucursal);when(c.isActivo()).thenReturn(true);when(c.getNombre()).thenReturn("Caja 01");when(cajas.bloquearParaOperacion(id,tenant,empresa)).thenReturn(Optional.of(c));return c;}
}
