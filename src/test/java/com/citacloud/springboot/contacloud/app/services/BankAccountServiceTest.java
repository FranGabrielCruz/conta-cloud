package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.CuentaBancariaInput;
import com.citacloud.springboot.contacloud.app.mappers.CuentaBancariaMapper;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BankAccountServiceTest {
    private final BankAccountRepository repository = mock(BankAccountRepository.class);
    private final MonedaRepository monedas = mock(MonedaRepository.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final BankAccountNumberService numbers = new BankAccountNumberService(
        "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8));
    private final BankAccountService service = new BankAccountService(repository, monedas,
        new CuentaBancariaMapper(numbers), numbers, auditoria);
    private final UUID tenantId = UUID.randomUUID();
    private final UUID empresaId = UUID.randomUUID();
    private final UUID usuarioId = UUID.randomUUID();
    private final UUID monedaId = UUID.randomUUID();
    private Moneda moneda;

    @BeforeEach
    void preparar() {
        var principal = new TenantPrincipal(usuarioId, tenantId, empresaId, null, "DEMO", "Administrador",
            "admin", "", true, true, Set.of(), Set.of("cuentas_bancarias.ver",
                "cuentas_bancarias.crear", "cuentas_bancarias.editar", "cuentas_bancarias.desactivar"));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        moneda = mock(Moneda.class);
        when(moneda.getId()).thenReturn(monedaId);
        when(moneda.getCodigoIso()).thenReturn("DOP");
        when(moneda.getNombre()).thenReturn("Peso dominicano");
        when(moneda.isActivo()).thenReturn(true);
        when(monedas.findByIdAndEmpresaId(monedaId, empresaId)).thenReturn(Optional.of(moneda));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void limpiar() { SecurityContextHolder.clearContext(); }

    @Test
    void creaCuentaNormalizadaCifradaYAuditada() {
        var dto = service.crear(entrada("  Banco Popular  ", "  Cuenta Principal  ", "1234-5678 90"));

        var captor = org.mockito.ArgumentCaptor.forClass(CuentaBancaria.class);
        verify(repository).saveAndFlush(captor.capture());
        CuentaBancaria entity = captor.getValue();
        assertThat(entity.getBancoNombre()).isEqualTo("Banco Popular");
        assertThat(entity.getNombreCuenta()).isEqualTo("Cuenta Principal");
        assertThat(entity.getNumeroCuentaCifrado()).doesNotContain("1234567890");
        assertThat(entity.getNumeroCuentaUltimos4()).isEqualTo("7890");
        assertThat(dto.numeroEnmascarado()).isEqualTo("•••• 7890");
        assertThat(dto.toString()).doesNotContain("1234567890");
        verify(auditoria).registrar(eq("BANK_ACCOUNT_CREATED"), eq("CuentaBancaria"), any(),
            argThat(text -> !text.contains("1234567890")));
    }

    @Test
    void validaCamposObligatoriosYTipo() {
        assertThatThrownBy(() -> service.crear(entrada(" ", "Cuenta", "12345678")))
            .hasMessage("El banco es obligatorio.");
        assertThatThrownBy(() -> service.crear(entrada("Banco", " ", "12345678")))
            .hasMessage("El nombre de cuenta es obligatorio.");
        var invalid = new CuentaBancariaInput("Banco", "Cuenta", null, monedaId, "12345678", null, true);
        assertThatThrownBy(() -> service.crear(invalid)).hasMessage("El tipo de cuenta es obligatorio.");
        var noCurrency = new CuentaBancariaInput("Banco", "Cuenta", TipoCuentaBancaria.CHECKING,
            null, "12345678", null, true);
        assertThatThrownBy(() -> service.crear(noCurrency)).hasMessage("La moneda es obligatoria.");
        assertThatThrownBy(() -> service.crear(entrada("Banco", "Cuenta", " ")))
            .hasMessage("El número de cuenta es obligatorio.");
    }

    @Test
    void rechazaMonedaDeOtraEmpresaOInactiva() {
        when(monedas.findByIdAndEmpresaId(monedaId, empresaId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.crear(entrada("Banco", "Cuenta", "12345678")))
            .hasMessage("La moneda seleccionada no es válida.");
    }

    @Test
    void detectaDuplicadoPorFingerprintNormalizado() {
        String fingerprint = numbers.fingerprint(numbers.normalize("1234-5678"));
        when(repository.existsByTenantIdAndEmpresaIdAndNumeroCuentaFingerprint(
            tenantId, empresaId, fingerprint)).thenReturn(true);
        assertThatThrownBy(() -> service.crear(entrada("Banco", "Cuenta", "1234 5678")))
            .hasMessage("Esta cuenta bancaria ya está registrada para la empresa.");
    }

    @Test
    void permiteNumerosDiferentesConMismosUltimosCuatro() {
        assertThatCode(() -> service.crear(entrada("Banco", "Cuenta A", "1234567890")))
            .doesNotThrowAnyException();
        assertThatCode(() -> service.crear(entrada("Banco", "Cuenta B", "9999997890")))
            .doesNotThrowAnyException();
        verify(repository, times(2)).saveAndFlush(any());
    }

    @Test
    void detalleNoExponeNumeroYEdicionAutorizadaLoRecupera() {
        UUID id = UUID.randomUUID();
        CuentaBancaria entity = entidad(id, true);
        when(repository.findByIdAndTenantIdAndEmpresaId(id, tenantId, empresaId)).thenReturn(Optional.of(entity));

        assertThat(service.obtener(id).numeroEnmascarado()).isEqualTo("•••• 7890");
        assertThat(service.obtener(id).toString()).doesNotContain("1234567890");
        assertThat(service.obtenerParaEditar(id).numeroCuenta()).isEqualTo("1234567890");
    }

    @Test
    void noConsultaCuentaDeOtroTenantOEmpresa() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantIdAndEmpresaId(id, tenantId, empresaId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.obtener(id)).isInstanceOf(RecursoNoEncontradoException.class)
            .hasMessage("Cuenta bancaria no encontrada.");
    }

    @Test
    void desactivaYReactivaSinEliminar() {
        UUID id = UUID.randomUUID();
        CuentaBancaria entity = entidad(id, true);
        when(repository.findByIdAndTenantIdAndEmpresaId(id, tenantId, empresaId)).thenReturn(Optional.of(entity));

        service.desactivar(id);
        assertThat(entity.isActivo()).isFalse();
        service.reactivar(id);
        assertThat(entity.isActivo()).isTrue();
        verify(repository, never()).delete(any());
        verify(auditoria).registrar(eq("BANK_ACCOUNT_DISABLED"), eq("CuentaBancaria"), eq(id), anyString());
        verify(auditoria).registrar(eq("BANK_ACCOUNT_ENABLED"), eq("CuentaBancaria"), eq(id), anyString());
    }

    private CuentaBancariaInput entrada(String banco, String nombre, String numero) {
        return new CuentaBancariaInput(banco, nombre, TipoCuentaBancaria.CHECKING, monedaId,
            numero, " Cuenta principal. ", true);
    }

    private CuentaBancaria entidad(UUID id, boolean activa) {
        String normalized = numbers.normalize("1234567890");
        CuentaBancaria entity = new CuentaBancaria(tenantId, empresaId, monedaId, "BAN-TEST",
            "Banco", "Cuenta", TipoCuentaBancaria.CHECKING, numbers.encrypt(normalized),
            numbers.last4(normalized), numbers.fingerprint(normalized), null, activa, usuarioId);
        ReflectionTestUtils.setField(entity, "id", id);
        entity.asignarMoneda(moneda);
        return entity;
    }
}
