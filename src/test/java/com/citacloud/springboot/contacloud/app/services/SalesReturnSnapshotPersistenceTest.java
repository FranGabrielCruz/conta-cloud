package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.DevolucionVentaRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop"})
class SalesReturnSnapshotPersistenceTest {
    @Autowired DevolucionVentaRepository repository;@Autowired EntityManager entityManager;
    @Test void confirmationSnapshotSurvivesPersistenceWithoutLookingUpCurrentCompanyOrCustomer(){
        UUID tenant=UUID.randomUUID(),company=UUID.randomUUID(),branch=UUID.randomUUID(),cash=UUID.randomUUID();
        var invoice=mock(FacturaVenta.class);when(invoice.getId()).thenReturn(UUID.randomUUID());when(invoice.getNumero()).thenReturn("FAC-ORIGINAL");when(invoice.getClienteNombre()).thenReturn("Cliente original");when(invoice.getMonedaId()).thenReturn(UUID.randomUUID());when(invoice.getMonedaCodigo()).thenReturn("DOP");
        var reason=mock(MotivoDevolucionVenta.class);when(reason.getId()).thenReturn(UUID.randomUUID());when(reason.getCodigo()).thenReturn("DEF");when(reason.getNombre()).thenReturn("Producto defectuoso");
        var d=new DevolucionVenta(tenant,company,"DEV-PRUEBA",invoice,reason,"Motivo histórico",false,null,null,new BigDecimal("12"),BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("2.16"),new BigDecimal("14.16"),UUID.randomUUID(),UUID.randomUUID());
        var issuer=new EmisorFacturaPos("Empresa al confirmar","124543678","Dirección histórica","8095551234","DO","Caja 02","America/Santo_Domingo");
        d.conservarIdentidadPos(issuer,branch,cash,"Sucursal histórica","Cajero histórico");UUID id=repository.saveAndFlush(d).getId();entityManager.clear();
        var saved=repository.findByIdAndTenantIdAndEmpresaId(id,tenant,company).orElseThrow();
        assertEquals("Empresa al confirmar",saved.getEmisorPos().getNombre());assertEquals("124543678",saved.getEmisorPos().getIdentificacion());assertEquals("Dirección histórica",saved.getEmisorPos().getDireccion());
        assertEquals("Cliente original",saved.getClienteNombre());assertEquals("FAC-ORIGINAL",saved.getFacturaNumero());assertEquals(cash,saved.getCajaPosId());assertEquals(branch,saved.getSucursalPosId());assertEquals("Cajero histórico",saved.getOperadorPosNombre());
        assertThrows(IllegalStateException.class,()->saved.conservarIdentidadPos(issuer,branch,cash,"Otro","Otro"));assertTrue(repository.findByIdAndTenantIdAndEmpresaId(id,UUID.randomUUID(),company).isEmpty());
    }
}
