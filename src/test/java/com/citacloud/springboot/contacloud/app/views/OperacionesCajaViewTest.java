package com.citacloud.springboot.contacloud.app.views;

import com.citacloud.springboot.contacloud.app.models.EstadoRevisionCaja;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperacionesCajaViewTest {
    @Test void permiteRevisarPendienteYRequiereRevisionConPermiso(){
        assertThat(OperacionesCajaView.puedeRevisar(EstadoRevisionCaja.PENDING,true)).isTrue();
        assertThat(OperacionesCajaView.puedeRevisar(EstadoRevisionCaja.REQUIRES_REVIEW,true)).isTrue();
    }

    @Test void noPermiteRevisarAprobadoAunqueTengaPermiso(){
        assertThat(OperacionesCajaView.puedeRevisar(EstadoRevisionCaja.APPROVED,true)).isFalse();
    }

    @Test void noPermiteRevisarSinPermiso(){
        assertThat(OperacionesCajaView.puedeRevisar(EstadoRevisionCaja.PENDING,false)).isFalse();
        assertThat(OperacionesCajaView.puedeRevisar(EstadoRevisionCaja.REQUIRES_REVIEW,false)).isFalse();
    }

    @Test void badgesTienenTextoExactoSinCaracteresResiduales(){
        assertThat(OperacionesCajaView.badgeRevision(EstadoRevisionCaja.PENDING).getText()).isEqualTo("Pendiente");
        assertThat(OperacionesCajaView.badgeRevision(EstadoRevisionCaja.APPROVED).getText()).isEqualTo("Aprobado");
        assertThat(OperacionesCajaView.badgeRevision(EstadoRevisionCaja.REQUIRES_REVIEW).getText()).isEqualTo("Requiere revisión");
    }
}
