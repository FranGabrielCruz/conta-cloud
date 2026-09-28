package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.PeriodoFiscalInput;
import com.citacloud.springboot.contacloud.app.mappers.PeriodoFiscalMapper;
import com.citacloud.springboot.contacloud.app.models.Empresa;
import com.citacloud.springboot.contacloud.app.models.PeriodoFiscal;
import com.citacloud.springboot.contacloud.app.repositories.EmpresaRepository;
import com.citacloud.springboot.contacloud.app.repositories.PeriodoFiscalRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountingPeriodServiceTest {
    private final PeriodoFiscalRepository repository = mock(PeriodoFiscalRepository.class);
    private final EmpresaRepository empresas = mock(EmpresaRepository.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final AccountingPeriodService service = new AccountingPeriodService(
        repository, empresas, new PeriodoFiscalMapper(), auditoria);
    private UUID tenantId;
    private UUID empresaId;

    @BeforeEach
    void autenticar() {
        tenantId = UUID.randomUUID();
        empresaId = UUID.randomUUID();
        var principal = new TenantPrincipal(UUID.randomUUID(), tenantId, empresaId, null,
            "EMPRESA01", "Administrador", "admin", "hash", true, true, Set.of(), Set.of());
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        when(empresas.findWithLockById(empresaId)).thenReturn(Optional.of(mock(Empresa.class)));
        when(repository.save(any(PeriodoFiscal.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void limpiar() { SecurityContextHolder.clearContext(); }

    @Test
    void rechazaFechaInicialPosteriorALaFinal() {
        assertThatThrownBy(() -> AccountingPeriodService.validar(new PeriodoFiscalInput(
            "Enero 2026", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 31))))
            .isInstanceOf(ReglaNegocioException.class)
            .hasMessage("La fecha inicial no puede ser posterior a la fecha final.");
    }

    @Test
    void rechazaUnPeriodoSolapado() {
        when(repository.existeSolapamiento(eq(tenantId), eq(empresaId), isNull(), any(), any()))
            .thenReturn(true);

        assertThatThrownBy(() -> service.crear(new PeriodoFiscalInput("Febrero 2026",
            LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 15))))
            .isInstanceOf(ReglaNegocioException.class)
            .hasMessage("Las fechas se solapan con otro período fiscal existente.");
    }

    @Test
    void permiteUnPeriodoAdyacente() {
        when(repository.existeSolapamiento(eq(tenantId), eq(empresaId), isNull(),
            eq(LocalDate.of(2026, 2, 1)), eq(LocalDate.of(2026, 2, 28)))).thenReturn(false);

        var creado = service.crear(new PeriodoFiscalInput("Febrero 2026",
            LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)));

        assertThat(creado.nombre()).isEqualTo("Febrero 2026");
        assertThat(creado.estado()).isEqualTo("ABIERTO");
    }

    @Test
    void periodoCerradoImpideContabilizar() {
        var entity = new PeriodoFiscal(tenantId, empresaId, "Enero 2026",
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        entity.cerrar(UUID.randomUUID());
        when(repository.findFirstByTenantIdAndEmpresaIdAndFechaInicialLessThanEqualAndFechaFinalGreaterThanEqual(
            tenantId, empresaId, LocalDate.of(2026, 1, 15), LocalDate.of(2026, 1, 15)))
            .thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.validarPeriodoAbierto(LocalDate.of(2026, 1, 15)))
            .isInstanceOf(ReglaNegocioException.class)
            .hasMessage("No se puede contabilizar la operación porque el período correspondiente está cerrado.");
    }

    @Test
    void periodoAbiertoPermiteContabilizar() {
        var entity = new PeriodoFiscal(tenantId, empresaId, "Febrero 2026",
            LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
        when(repository.findFirstByTenantIdAndEmpresaIdAndFechaInicialLessThanEqualAndFechaFinalGreaterThanEqual(
            tenantId, empresaId, LocalDate.of(2026, 2, 15), LocalDate.of(2026, 2, 15)))
            .thenReturn(Optional.of(entity));

        assertThat(service.validarPeriodoAbierto(LocalDate.of(2026, 2, 15)).estado()).isEqualTo("ABIERTO");
    }

    @Test
    void consultaSiempreUsaTenantYEmpresaAutenticados() {
        UUID idAjeno = UUID.randomUUID();
        when(repository.findByIdAndTenantIdAndEmpresaId(idAjeno, tenantId, empresaId))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtener(idAjeno))
            .isInstanceOf(RecursoNoEncontradoException.class);
        verify(repository).findByIdAndTenantIdAndEmpresaId(idAjeno, tenantId, empresaId);
    }
}
