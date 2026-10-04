package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.ProductoCategoria;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop"})
class ProductoCategoriaRepositoryTest {
    @Autowired private ProductoCategoriaRepository repository;

    @Test void persistsSearchesFiltersAndIsolatesByCompany(){UUID tenant=UUID.randomUUID(),companyA=UUID.randomUUID(),companyB=UUID.randomUUID(),user=UUID.randomUUID();repository.saveAndFlush(category(tenant,companyA,user,"Alimentos","alimentos",true));repository.saveAndFlush(category(tenant,companyA,user,"Bebidas","bebidas",false));repository.saveAndFlush(category(tenant,companyB,user,"Alimentos","alimentos",true));assertThat(repository.buscar(tenant,companyA,"alimento",true,PageRequest.of(0,10))).hasSize(1).allSatisfy(c->assertThat(c.getEmpresaId()).isEqualTo(companyA));assertThat(repository.buscar(tenant,companyA,"",null,PageRequest.of(0,1)).getTotalElements()).isEqualTo(2);}

    @Test void enforcesNormalizedNameUniquenessInsideCompany(){UUID tenant=UUID.randomUUID(),company=UUID.randomUUID(),user=UUID.randomUUID();repository.saveAndFlush(category(tenant,company,user,"Alimentos","alimentos",true));assertThatThrownBy(()->repository.saveAndFlush(category(tenant,company,user," alimentos ","alimentos",true))).isInstanceOf(DataIntegrityViolationException.class);}

    private static ProductoCategoria category(UUID tenant,UUID company,UUID user,String name,String normalized,boolean active){return new ProductoCategoria(tenant,company,name,normalized,null,active,user);}
}
