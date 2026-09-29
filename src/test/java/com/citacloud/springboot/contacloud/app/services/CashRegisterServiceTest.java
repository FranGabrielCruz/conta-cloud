package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.CajaInput;
import com.citacloud.springboot.contacloud.app.mappers.CajaMapper;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CashRegisterServiceTest {
    private final CashRegisterRepository repository = mock(CashRegisterRepository.class);
    private final SucursalRepository sucursales = mock(SucursalRepository.class);
    private final MonedaRepository monedas = mock(MonedaRepository.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final CashRegisterService service = new CashRegisterService(repository, sucursales, monedas,
        new CajaMapper(), auditoria);
    private final UUID tenantId = UUID.randomUUID();
    private final UUID empresaId = UUID.randomUUID();
    private final UUID usuarioId = UUID.randomUUID();
    private final UUID sucursalId = UUID.randomUUID();
    private final UUID monedaId = UUID.randomUUID();
    private Sucursal sucursal;
    private Moneda moneda;

    @BeforeEach
    void preparar() {
        var principal = new TenantPrincipal(usuarioId, tenantId, empresaId, null, "DEMO", "Administrador",
            "admin", "", true, true, Set.of(),
            Set.of("cajas.ver", "cajas.crear", "cajas.editar", "cajas.desactivar"));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        sucursal = mock(Sucursal.class);
        when(sucursal.getId()).thenReturn(sucursalId);
        when(sucursal.getNombre()).thenReturn("Principal");
        when(sucursal.isActivo()).thenReturn(true);
        moneda = mock(Moneda.class);
        when(moneda.getId()).thenReturn(monedaId);
        when(moneda.getCodigoIso()).thenReturn("DOP");
        when(moneda.getNombre()).thenReturn("Peso dominicano");
        when(moneda.isActivo()).thenReturn(true);
        when(sucursales.findByIdAndEmpresaId(sucursalId, empresaId)).thenReturn(Optional.of(sucursal));
        when(monedas.findByIdAndEmpresaId(monedaId, empresaId)).thenReturn(Optional.of(moneda));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void limpiar() { SecurityContextHolder.clearContext(); }

    @Test
    void creaCajaConNombreNormalizadoUnaSucursalYUnaMoneda() {
        service.crear(new CajaInput("  Caja Principal  ", sucursalId, monedaId,
            "  Caja principal de la sucursal.  ", true));

        var captor = org.mockito.ArgumentCaptor.forClass(Caja.class);
        verify(repository).saveAndFlush(captor.capture());
        Caja caja = captor.getValue();
        assertThat(caja.getNombre()).isEqualTo("Caja Principal");
        assertThat(caja.getDescripcion()).isEqualTo("Caja principal de la sucursal.");
        assertThat(caja.getSucursalId()).isEqualTo(sucursalId);
        assertThat(caja.getMonedaId()).isEqualTo(monedaId);
        assertThat(caja.getCodigo()).startsWith("CAJ-");
        assertThat(caja.isActivo()).isTrue();
        verify(auditoria).registrar(eq("CASH_REGISTER_CREATED"), eq("Caja"), any(), anyString());
    }

    @Test
    void validaCamposObligatorios() {
        assertThatThrownBy(() -> service.crear(new CajaInput("   ", sucursalId, monedaId, null, true)))
            .isInstanceOf(ReglaNegocioException.class).hasMessage("El nombre es obligatorio.");
        assertThatThrownBy(() -> service.crear(new CajaInput("Caja", null, monedaId, null, true)))
            .isInstanceOf(ReglaNegocioException.class).hasMessage("La sucursal es obligatoria.");
        assertThatThrownBy(() -> service.crear(new CajaInput("Caja", sucursalId, null, null, true)))
            .isInstanceOf(ReglaNegocioException.class).hasMessage("La moneda es obligatoria.");
    }

    @Test
    void rechazaSucursalDeOtraEmpresaOInactiva() {
        when(sucursales.findByIdAndEmpresaId(sucursalId, empresaId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.crear(entrada("Caja")))
            .isInstanceOf(ReglaNegocioException.class)
            .hasMessage("La sucursal seleccionada no es válida.");
    }

    @Test
    void rechazaMonedaDeOtraEmpresaOInactiva() {
        when(monedas.findByIdAndEmpresaId(monedaId, empresaId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.crear(entrada("Caja")))
            .isInstanceOf(ReglaNegocioException.class)
            .hasMessage("La moneda seleccionada no es válida.");
    }

    @Test
    void rechazaNombreDuplicadoEnLaMismaSucursal() {
        when(repository.existsByTenantIdAndEmpresaIdAndSucursalIdAndNombreIgnoreCase(
            tenantId, empresaId, sucursalId, "Caja Principal")).thenReturn(true);
        assertThatThrownBy(() -> service.crear(entrada("Caja Principal")))
            .isInstanceOf(ReglaNegocioException.class)
            .hasMessage("Ya existe una caja con este nombre en la sucursal seleccionada.");
    }

    @Test
    void permiteMismoNombreCuandoLaSucursalEsDistinta() {
        UUID otraSucursalId = UUID.randomUUID();
        Sucursal otra = mock(Sucursal.class);
        when(otra.getId()).thenReturn(otraSucursalId);
        when(otra.getNombre()).thenReturn("Santiago");
        when(otra.isActivo()).thenReturn(true);
        when(sucursales.findByIdAndEmpresaId(otraSucursalId, empresaId)).thenReturn(Optional.of(otra));

        assertThatCode(() -> service.crear(new CajaInput("Caja Principal", otraSucursalId, monedaId, null, true)))
            .doesNotThrowAnyException();
        verify(repository).existsByTenantIdAndEmpresaIdAndSucursalIdAndNombreIgnoreCase(
            tenantId, empresaId, otraSucursalId, "Caja Principal");
    }

    @Test
    void noConsultaCajaDeOtroTenantOEmpresa() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantIdAndEmpresaId(id, tenantId, empresaId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.obtener(id)).isInstanceOf(RecursoNoEncontradoException.class)
            .hasMessage("Caja no encontrada.");
    }

    @Test
    void desactivaYReactivaSinEliminar() {
        UUID id = UUID.randomUUID();
        Caja caja = new Caja(tenantId, empresaId, sucursalId, monedaId, "CAJ-TEST", "Caja", null,
            true, usuarioId);
        ReflectionTestUtils.setField(caja, "id", id);
        caja.asignarSucursal(sucursal);
        caja.asignarMoneda(moneda);
        when(repository.findByIdAndTenantIdAndEmpresaId(id, tenantId, empresaId)).thenReturn(Optional.of(caja));

        service.desactivar(id);
        assertThat(caja.isActivo()).isFalse();
        service.reactivar(id);
        assertThat(caja.isActivo()).isTrue();
        verify(repository, never()).delete(any());
        verify(auditoria).registrar(eq("CASH_REGISTER_DISABLED"), eq("Caja"), eq(id), anyString());
        verify(auditoria).registrar(eq("CASH_REGISTER_ENABLED"), eq("Caja"), eq(id), anyString());
    }

    private CajaInput entrada(String nombre) {
        return new CajaInput(nombre, sucursalId, monedaId, null, true);
    }
}
