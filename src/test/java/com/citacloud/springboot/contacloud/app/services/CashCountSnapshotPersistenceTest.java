package com.citacloud.springboot.contacloud.app.services;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.RecuentoCajaRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop"})
class CashCountSnapshotPersistenceTest {
    @Autowired RecuentoCajaRepository repository;@Autowired EntityManager em;
    @Test void snapshotCapturadoDespuesDeInsertarSePersisteYNoSeSobrescribe(){
        for(String[] column:new String[][]{{"empresas","creado_en"},{"cajas","creado_en"},{"cash_register_session","opened_at"},{"cash_register_session","created_at"}})
            em.createNativeQuery("alter table "+column[0]+" alter column "+column[1]+" set default current_timestamp").executeUpdate();
        UUID tenant=UUID.randomUUID();var company=new Empresa(tenant,"CC-"+UUID.randomUUID().toString().substring(0,8),"Empresa");em.persist(company);
        var user=new Usuario(tenant,company.getId(),"cajero","Juan","Pérez","test-only-placeholder");em.persist(user);
        var branch=new Sucursal(company.getId(),"S01","Principal");em.persist(branch);var currency=new Moneda(company.getId(),"DOP","Peso","$",(short)2);em.persist(currency);
        var cash=new Caja(tenant,company.getId(),branch.getId(),currency.getId(),"C01","Caja 01",null,true,user.getId());em.persist(cash);ReflectionTestUtils.setField(cash,"sucursal",branch);ReflectionTestUtils.setField(cash,"moneda",currency);
        var session=new SesionCaja(tenant,company.getId(),cash,LocalDate.of(2026,11,1),1,"TURN-01",BigDecimal.TEN,null,user.getId());em.persist(session);ReflectionTestUtils.setField(session,"usuarioApertura",user);
        var count=new RecuentoCaja(tenant,company.getId(),"RCJ-000001",session,OffsetDateTime.parse("2026-11-01T20:15:00Z"),user.getId(),BigDecimal.TEN,BigDecimal.TEN,null,UUID.randomUUID());repository.saveAndFlush(count);
        // Capture follows the business/detail flush and must therefore be updateable once.
        count.conservarDocumentoPos("{\"historical\":true}");repository.flush();UUID id=count.getId();em.clear();
        var saved=repository.findByIdAndTenantIdAndEmpresaId(id,tenant,company.getId()).orElseThrow();assertThat(saved.getDocumentoPos()).isEqualTo("{\"historical\":true}");assertThatThrownBy(()->saved.conservarDocumentoPos("replacement")).isInstanceOf(IllegalStateException.class);
        assertThat(repository.findByIdAndTenantIdAndEmpresaId(id,UUID.randomUUID(),company.getId())).isEmpty();
    }
}
