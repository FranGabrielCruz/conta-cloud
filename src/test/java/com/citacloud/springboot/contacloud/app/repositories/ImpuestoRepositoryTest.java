package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop"})
class ImpuestoRepositoryTest {
    @Autowired private ImpuestoRepository repository;
    @Test void persistsSearchesFiltersAndIsolatesByCompany(){UUID tenant=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID(),user=UUID.randomUUID();repository.saveAndFlush(tax(tenant,a,user,"ITBIS 18%","itbis 18%",true));repository.saveAndFlush(tax(tenant,a,user,"Exento","exento",false));repository.saveAndFlush(tax(tenant,b,user,"ITBIS 18%","itbis 18%",true));assertThat(repository.buscar(tenant,a,"itbis",true,PageRequest.of(0,10))).hasSize(1).allSatisfy(t->assertThat(t.getEmpresaId()).isEqualTo(a));assertThat(repository.buscar(tenant,a,"",null,PageRequest.of(0,1)).getTotalElements()).isEqualTo(2);}
    @Test void enforcesNormalizedNameUniquenessPerCompany(){UUID tenant=UUID.randomUUID(),company=UUID.randomUUID(),user=UUID.randomUUID();repository.saveAndFlush(tax(tenant,company,user,"ITBIS","itbis",true));assertThatThrownBy(()->repository.saveAndFlush(tax(tenant,company,user," itbis ","itbis",true))).isInstanceOf(DataIntegrityViolationException.class);}
    private static Impuesto tax(UUID tenant,UUID company,UUID user,String name,String normalized,boolean active){return new Impuesto(tenant,company,"IMP-"+UUID.randomUUID().toString().substring(0,8),name,normalized,new BigDecimal("18"),TipoImpuesto.PERCENTAGE,null,active,user);}
}
