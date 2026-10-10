package com.citacloud.springboot.contacloud.app.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SupplierPaymentRepositoryQueryTest {

    @Autowired
    private SupplierPaymentRepository repository;

    @Test
    void consultaDeChequeActivoUsaAtributosJpaValidos() {
        assertThat(repository).isNotNull();
    }
}
