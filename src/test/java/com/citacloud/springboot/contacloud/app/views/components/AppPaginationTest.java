package com.citacloud.springboot.contacloud.app.views.components;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppPaginationTest {
    @Test void permiteConfigurarCincoRegistrosComoValorPredeterminado(){
        AppPagination pagination=new AppPagination(request->{},5,5,10,25,50);

        assertThat(pagination.currentRequest().page()).isZero();
        assertThat(pagination.currentRequest().size()).isEqualTo(5);
    }
}
