package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.FacturaVentaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Runs only against Spring's embedded test database, never the configured PostgreSQL database. */
@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop"})
class EmisorFacturaPosPersistenceTest {
    @Autowired FacturaVentaRepository repository;
    @Autowired EntityManager entityManager;
    @Test void issuerSurvivesPersistenceAndCannotBeReplaced() {
        entityManager.createNativeQuery("alter table sales_invoice alter column created_at set default current_timestamp").executeUpdate();
        UUID tenant=UUID.randomUUID(), company=UUID.randomUUID();
        var f=new FacturaVenta(tenant,company,"FAC-SNAPSHOT",LocalDate.of(2026,11,1),UUID.randomUUID(),UUID.randomUUID(),
                UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),null,"Consumidor Final",null,"José Pérez",
                UUID.randomUUID(),"DOP","RD$",null,BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.TEN,BigDecimal.TEN,UUID.randomUUID());
        var issuer=new EmisorFacturaPos("Emisor original","124543678","Dirección original","8095551234","DO","Caja original","America/Santo_Domingo");
        f.conservarEmisorPos(issuer);UUID id=repository.saveAndFlush(f).getId();entityManager.clear();
        var persisted=repository.findByIdAndTenantIdAndEmpresaId(id,tenant,company).orElseThrow();
        assertEquals("Emisor original",persisted.getEmisorPos().getNombre());assertEquals("124543678",persisted.getEmisorPos().getIdentificacion());
        assertEquals("Dirección original",persisted.getEmisorPos().getDireccion());assertEquals("8095551234",persisted.getEmisorPos().getTelefono());
        assertEquals("DO",persisted.getEmisorPos().getPais());assertEquals("Caja original",persisted.getEmisorPos().getCaja());
        assertEquals("America/Santo_Domingo",persisted.getEmisorPos().getZona());
        assertThrows(IllegalStateException.class,()->persisted.conservarEmisorPos(issuer));
        assertTrue(repository.findByIdAndTenantIdAndEmpresaId(id,UUID.randomUUID(),company).isEmpty());
    }
}
