package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.TasaCambioInput;
import com.citacloud.springboot.contacloud.app.mappers.TasaCambioMapper;
import com.citacloud.springboot.contacloud.app.models.Moneda;
import com.citacloud.springboot.contacloud.app.models.TasaCambio;
import com.citacloud.springboot.contacloud.app.repositories.MonedaRepository;
import com.citacloud.springboot.contacloud.app.repositories.TasaCambioRepository;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExchangeRateServiceTest {
    private final TasaCambioRepository repository = mock(TasaCambioRepository.class);
    private final MonedaRepository monedas = mock(MonedaRepository.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final ExchangeRateService service = new ExchangeRateService(repository, monedas,
        new TasaCambioMapper(), auditoria);
    private final UUID tenantId = UUID.randomUUID();
    private final UUID empresaId = UUID.randomUUID();
    private final UUID usuarioId = UUID.randomUUID();
    private final UUID origenId = UUID.randomUUID();
    private final UUID destinoId = UUID.randomUUID();
    private Moneda origen;
    private Moneda destino;

    @BeforeEach
    void autenticar() {
        var principal = new TenantPrincipal(usuarioId, tenantId, empresaId, null, "EMPRESA", "Usuario",
            "usuario", "", true, true, Set.of(),
            Set.of("tasas_cambio.ver", "tasas_cambio.crear", "tasas_cambio.editar", "tasas_cambio.desactivar"));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        origen = moneda(origenId, empresaId, "USD", true);
        destino = moneda(destinoId, empresaId, "DOP", true);
        when(monedas.findByIdAndEmpresaId(origenId, empresaId)).thenReturn(Optional.of(origen));
        when(monedas.findByIdAndEmpresaId(destinoId, empresaId)).thenReturn(Optional.of(destino));
    }

    @AfterEach
    void limpiarContexto() { SecurityContextHolder.clearContext(); }

    @Test
    void rechazaMonedasIguales() {
        var input = new TasaCambioInput(origenId, origenId, new BigDecimal("1.00000000"), LocalDate.now(), true);
        assertThatThrownBy(() -> service.crear(input))
            .isInstanceOf(ReglaNegocioException.class)
            .hasMessage("La moneda de origen y la moneda de destino deben ser diferentes.");
        verifyNoInteractions(repository);
    }

    @Test
    void rechazaTasaNoPositiva() {
        var input = new TasaCambioInput(origenId, destinoId, BigDecimal.ZERO, LocalDate.now(), true);
        assertThatThrownBy(() -> service.crear(input))
            .isInstanceOf(ReglaNegocioException.class)
            .hasMessage("La tasa de cambio debe ser mayor que cero.");
    }

    @Test
    void conservaOchoDecimalesAlCrear() {
        LocalDate fecha = LocalDate.of(2026, 9, 28);
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.crear(new TasaCambioInput(origenId, destinoId, new BigDecimal("63.25123456"), fecha, true));
        var captor = org.mockito.ArgumentCaptor.forClass(TasaCambio.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getTasa()).isEqualByComparingTo("63.25123456");
    }

    @Test
    void rechazaDuplicadoConMensajeFuncional() {
        LocalDate fecha = LocalDate.of(2026, 9, 28);
        when(repository.existsByTenantIdAndEmpresaIdAndMonedaOrigenIdAndMonedaDestinoIdAndFecha(
            tenantId, empresaId, origenId, destinoId, fecha)).thenReturn(true);
        assertThatThrownBy(() -> service.crear(new TasaCambioInput(origenId, destinoId,
            new BigDecimal("63.25"), fecha, true)))
            .isInstanceOf(ReglaNegocioException.class)
            .hasMessage("Ya existe una tasa de cambio para USD → DOP en la fecha 28/09/2026.");
    }

    @Test
    void rechazaMonedaDeOtraEmpresa() {
        UUID ajena = UUID.randomUUID();
        when(monedas.findByIdAndEmpresaId(ajena, empresaId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.crear(new TasaCambioInput(origenId, ajena,
            new BigDecimal("63.25"), LocalDate.now(), true)))
            .isInstanceOf(ReglaNegocioException.class)
            .hasMessage("Seleccione una moneda válida de la empresa actual.");
    }

    @Test
    void resuelveTasaInversaSoloCuandoSeAutoriza() {
        LocalDate fecha = LocalDate.of(2026, 9, 28);
        TasaCambio inversa = new TasaCambio(tenantId, empresaId, destinoId, origenId,
            new BigDecimal("63.25000000"), fecha, true, usuarioId);
        when(repository.findByTenantIdAndEmpresaIdAndMonedaOrigenIdAndMonedaDestinoIdAndFechaAndActivoTrue(
            tenantId, empresaId, origenId, destinoId, fecha)).thenReturn(Optional.empty());
        when(repository.findByTenantIdAndEmpresaIdAndMonedaOrigenIdAndMonedaDestinoIdAndFechaAndActivoTrue(
            tenantId, empresaId, destinoId, origenId, fecha)).thenReturn(Optional.of(inversa));
        var result = service.resolverExacta(origenId, destinoId, fecha, true);
        assertThat(result.derivada()).isTrue();
        assertThat(result.tasa()).isEqualByComparingTo("0.01581028");
    }

    private static Moneda moneda(UUID id, UUID empresaId, String codigo, boolean activa) {
        Moneda moneda = new Moneda(empresaId, codigo, codigo, "$", (short) 2);
        try {
            var field = Moneda.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(moneda, id);
        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
        moneda.setActivo(activa);
        return moneda;
    }
}
