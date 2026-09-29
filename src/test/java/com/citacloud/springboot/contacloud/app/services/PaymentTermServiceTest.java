package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.CondicionPagoDto;
import com.citacloud.springboot.contacloud.app.dto.CondicionPagoInput;
import com.citacloud.springboot.contacloud.app.mappers.CondicionPagoMapper;
import com.citacloud.springboot.contacloud.app.models.CondicionPago;
import com.citacloud.springboot.contacloud.app.models.TipoCondicionPago;
import com.citacloud.springboot.contacloud.app.repositories.CondicionPagoRepository;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentTermServiceTest {
    private final CondicionPagoRepository repository = mock(CondicionPagoRepository.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final PaymentTermService service = new PaymentTermService(repository, new CondicionPagoMapper(), auditoria);
    private final UUID tenantId = UUID.randomUUID();
    private final UUID empresaId = UUID.randomUUID();
    private final UUID usuarioId = UUID.randomUUID();

    @BeforeEach
    void autenticar() {
        var principal = new TenantPrincipal(usuarioId, tenantId, empresaId, null, "EMPRESA", "Usuario",
            "usuario", "", true, true, Set.of(), Set.of("condiciones_pago.ver", "condiciones_pago.crear",
            "condiciones_pago.editar", "condiciones_pago.desactivar"));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void limpiarContexto() { SecurityContextHolder.clearContext(); }

    @Test
    void creaConNombreNormalizadoYCodigoInterno() {
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.crear(new CondicionPagoInput("  Crédito 30 días  ", TipoCondicionPago.CREDIT, 30,
            "  Pago mensual.  ", true));
        var captor = org.mockito.ArgumentCaptor.forClass(CondicionPago.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getNombre()).isEqualTo("Crédito 30 días");
        assertThat(captor.getValue().getCodigo()).startsWith("CP-");
        assertThat(captor.getValue().getDescripcion()).isEqualTo("Pago mensual.");
    }

    @Test
    void rechazaNombreVacio() {
        assertThatThrownBy(() -> service.crear(new CondicionPagoInput("   ", TipoCondicionPago.CASH, 0, null, true)))
            .isInstanceOf(ReglaNegocioException.class).hasMessage("El nombre es obligatorio.");
    }

    @Test
    void contadoExigeCeroDias() {
        assertThatThrownBy(() -> service.crear(new CondicionPagoInput("Contado", TipoCondicionPago.CASH, 30, null, true)))
            .isInstanceOf(ReglaNegocioException.class)
            .hasMessage("Las condiciones de contado deben tener 0 días para vencimiento.");
    }

    @Test
    void creditoExigeDiasMayoresQueCero() {
        assertThatThrownBy(() -> service.crear(new CondicionPagoInput("Crédito", TipoCondicionPago.CREDIT, 0, null, true)))
            .isInstanceOf(ReglaNegocioException.class)
            .hasMessage("Los días para vencimiento deben ser mayores que cero.");
    }

    @Test
    void limitaPlazoAMilDiezAnios() {
        assertThatThrownBy(() -> service.crear(new CondicionPagoInput("Crédito largo", TipoCondicionPago.CREDIT,
            3651, null, true))).isInstanceOf(ReglaNegocioException.class)
            .hasMessage("Los días para vencimiento deben estar entre 0 y 3650.");
    }

    @Test
    void rechazaNombreDuplicadoPorEmpresa() {
        when(repository.existsByTenantIdAndEmpresaIdAndNombreIgnoreCase(tenantId, empresaId, "Crédito 30 días"))
            .thenReturn(true);
        assertThatThrownBy(() -> service.crear(new CondicionPagoInput("Crédito 30 días",
            TipoCondicionPago.CREDIT, 30, null, true)))
            .isInstanceOf(ReglaNegocioException.class)
            .hasMessage("Ya existe una condición de pago con este nombre.");
    }

    @Test
    void calculaVencimientosConDiasCalendario() {
        LocalDate documento = LocalDate.of(2026, 9, 28);
        var contado = new CondicionPagoDto(UUID.randomUUID(), "Contado", TipoCondicionPago.CASH, 0, null, true);
        var credito = new CondicionPagoDto(UUID.randomUUID(), "Crédito 30", TipoCondicionPago.CREDIT, 30, null, true);
        assertThat(service.calculateDueDate(documento, contado)).isEqualTo(LocalDate.of(2026, 9, 28));
        assertThat(service.calculateDueDate(documento, credito)).isEqualTo(LocalDate.of(2026, 10, 28));
        assertThat(service.calculateDueDate(LocalDate.of(2026, 1, 31), credito))
            .isEqualTo(LocalDate.of(2026, 3, 2));
        assertThat(service.calculateDueDate(LocalDate.of(2024, 2, 1), credito))
            .isEqualTo(LocalDate.of(2024, 3, 2));
    }

    @Test
    void noConsultaCondicionDeOtraEmpresa() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantIdAndEmpresaId(id, tenantId, empresaId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.calculateDueDate(LocalDate.now(), id))
            .isInstanceOf(RecursoNoEncontradoException.class)
            .hasMessage("Condición de pago no encontrada.");
    }
}
