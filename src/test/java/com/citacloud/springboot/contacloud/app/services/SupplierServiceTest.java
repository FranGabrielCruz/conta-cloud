package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.ProveedorMapper;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.domain.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SupplierServiceTest {
    private final SupplierRepository suppliers=mock(SupplierRepository.class);
    private final CondicionPagoRepository condiciones=mock(CondicionPagoRepository.class);
    private final MonedaRepository monedas=mock(MonedaRepository.class);
    private final AuditoriaService auditoria=mock(AuditoriaService.class);
    private final SupplierService service=new SupplierService(suppliers,condiciones,monedas,new ProveedorMapper(),auditoria);
    private final UUID tenant=UUID.randomUUID(),empresa=UUID.randomUUID(),usuario=UUID.randomUUID();
    private final UUID condicionId=UUID.randomUUID(),monedaId=UUID.randomUUID();

    @BeforeEach void preparar(){var principal=new TenantPrincipal(usuario,tenant,empresa,null,"DEMO","Administrador","admin","",true,true,Set.of(),
        Set.of("proveedores.ver","proveedores.crear","proveedores.editar","proveedores.desactivar","proveedores.reactivar"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        CondicionPago condicion=mock(CondicionPago.class);when(condicion.isActivo()).thenReturn(true);
        when(condiciones.findByIdAndTenantIdAndEmpresaId(condicionId,tenant,empresa)).thenReturn(Optional.of(condicion));
        Moneda moneda=mock(Moneda.class);when(moneda.isActivo()).thenReturn(true);
        when(monedas.findByIdAndEmpresaId(monedaId,empresa)).thenReturn(Optional.of(moneda));
        when(suppliers.saveAndFlush(any())).thenAnswer(inv->{Proveedor p=inv.getArgument(0);if(p.getId()==null)ReflectionTestUtils.setField(p,"id",UUID.randomUUID());return p;});}
    @AfterEach void limpiar(){SecurityContextHolder.clearContext();}

    @Test void creaProveedorNormalizadoYAudita(){ProveedorDto creado=service.createSupplier(input("  Distribuidora Central  ","1-31-45678-9",TipoProveedor.NATIONAL));
        assertThat(creado.nombreComercial()).isEqualTo("Distribuidora Central");assertThat(creado.identificacionFiscal()).isEqualTo("131-45678-9");
        assertThat(creado.telefono()).isEqualTo("(809) 555-0202");assertThat(creado.correo()).isEqualTo("ventas@proveedor.com");
        verify(auditoria).registrar(eq("SUPPLIER_CREATED"),eq("Proveedor"),any(),anyString());}

    @Test void exigeNombreYTipo(){assertThatThrownBy(()->service.createSupplier(input("   ","131456789",TipoProveedor.NATIONAL)))
        .hasMessage("El nombre comercial es obligatorio.");ProveedorInput sinTipo=new ProveedorInput("Proveedor",null,null,null,null,null,null,null,null,null,null,null,true);
        assertThatThrownBy(()->service.createSupplier(sinTipo)).hasMessage("El tipo de proveedor es obligatorio.");}

    @Test void validaRncNacionalPeroAceptaIdentificacionExtranjera(){assertThatThrownBy(()->service.createSupplier(input("Proveedor","ABC-12",TipoProveedor.NATIONAL)))
        .hasMessage("El RNC no tiene un formato válido.");ProveedorDto extranjero=service.createSupplier(input("Exterior","US-TAX-ABC-12",TipoProveedor.FOREIGN));
        assertThat(extranjero.identificacionFiscal()).isEqualTo("USTAXABC12");}

    @Test void rechazaCorreoInvalido(){ProveedorInput base=input("Proveedor","131456789",TipoProveedor.NATIONAL);
        ProveedorInput invalido=new ProveedorInput(base.nombreComercial(),null,base.identificacionFiscal(),base.tipo(),base.telefono(),"correo-invalido",
            base.condicionPagoId(),base.monedaId(),null,null,null,null,true);
        assertThatThrownBy(()->service.createSupplier(invalido)).hasMessage("El correo electrónico no es válido.");}

    @Test void detectaIdentificacionDuplicadaNormalizada(){when(suppliers.existsByTenantIdAndEmpresaIdAndIdentificacionFiscalNormalizada(tenant,empresa,"131456789")).thenReturn(true);
        assertThatThrownBy(()->service.createSupplier(input("Proveedor","1-31-45678-9",TipoProveedor.NATIONAL)))
            .hasMessage("Ya existe un proveedor con este RNC o identificación.");verify(suppliers,never()).saveAndFlush(any());}

    @Test void rechazaCondicionYMonedaFueraDeLaEmpresa(){when(condiciones.findByIdAndTenantIdAndEmpresaId(condicionId,tenant,empresa)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.createSupplier(input("Proveedor","131456789",TipoProveedor.NATIONAL)))
            .hasMessage("La condición de pago seleccionada no está disponible.");
        CondicionPago condicion=mock(CondicionPago.class);when(condicion.isActivo()).thenReturn(true);when(condiciones.findByIdAndTenantIdAndEmpresaId(condicionId,tenant,empresa)).thenReturn(Optional.of(condicion));
        when(monedas.findByIdAndEmpresaId(monedaId,empresa)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.createSupplier(input("Proveedor","131456789",TipoProveedor.NATIONAL)))
            .hasMessage("La moneda seleccionada no está disponible.");}

    @Test void actualizaValoresPredeterminadosSinCambiarIdentidadTenant(){Proveedor p=proveedor(true);when(suppliers.findByIdAndTenantIdAndEmpresaId(p.getId(),tenant,empresa)).thenReturn(Optional.of(p));
        UUID nuevaCondicion=UUID.randomUUID(),nuevaMoneda=UUID.randomUUID();CondicionPago c=mock(CondicionPago.class);when(c.isActivo()).thenReturn(true);
        Moneda m=mock(Moneda.class);when(m.isActivo()).thenReturn(true);when(condiciones.findByIdAndTenantIdAndEmpresaId(nuevaCondicion,tenant,empresa)).thenReturn(Optional.of(c));when(monedas.findByIdAndEmpresaId(nuevaMoneda,empresa)).thenReturn(Optional.of(m));
        ProveedorInput cambio=new ProveedorInput("Proveedor actualizado",null,"131456789",TipoProveedor.NATIONAL,null,null,nuevaCondicion,nuevaMoneda,null,null,null,null,true);
        service.updateSupplier(p.getId(),cambio);assertThat(p.getCondicionPagoId()).isEqualTo(nuevaCondicion);assertThat(p.getMonedaId()).isEqualTo(nuevaMoneda);
        assertThat(p.getTenantId()).isEqualTo(tenant);assertThat(p.getEmpresaId()).isEqualTo(empresa);verify(auditoria).registrar(eq("SUPPLIER_UPDATED"),eq("Proveedor"),eq(p.getId()),anyString());}

    @Test void desactivaYReactivaSinEliminar(){Proveedor p=proveedor(true);when(suppliers.findByIdAndTenantIdAndEmpresaId(p.getId(),tenant,empresa)).thenReturn(Optional.of(p));
        service.deactivateSupplier(p.getId());assertThat(p.isActivo()).isFalse();verify(suppliers,never()).delete(any());
        service.reactivateSupplier(p.getId());assertThat(p.isActivo()).isTrue();
        verify(auditoria).registrar(eq("SUPPLIER_DEACTIVATED"),eq("Proveedor"),eq(p.getId()),anyString());verify(auditoria).registrar(eq("SUPPLIER_REACTIVATED"),eq("Proveedor"),eq(p.getId()),anyString());}

    @Test void noExponeProveedorDeOtroContexto(){UUID id=UUID.randomUUID();when(suppliers.findByIdAndTenantIdAndEmpresaId(id,tenant,empresa)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.getSupplier(id)).isInstanceOf(RecursoNoEncontradoException.class).hasMessage("Proveedor no encontrado.");}

    @Test void buscaPaginadoEnBackendConIdentificacionNormalizada(){when(suppliers.buscar(eq(tenant),eq(empresa),eq("1-31-45678-9"),eq("131456789"),eq(true),any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(proveedor(true))));
        var page=service.searchSuppliers("1-31-45678-9",true,0,10,"nombre",true);
        assertThat(page.getContent()).hasSize(1);verify(suppliers).buscar(eq(tenant),eq(empresa),eq("1-31-45678-9"),eq("131456789"),eq(true),argThat(p->p.getPageNumber()==0&&p.getPageSize()==10));}

    @Test void muestraCreditoParaNombreHeredadoFiao(){CondicionPago condicion=mock(CondicionPago.class);when(condicion.getNombre()).thenReturn("fiao");when(condicion.getTipo()).thenReturn(TipoCondicionPago.CREDIT);
        assertThat(SupplierService.nombreCondicion(condicion)).isEqualTo("Crédito");}

    private ProveedorInput input(String nombre,String identificacion,TipoProveedor tipo){return new ProveedorInput(nombre,"Razón social",identificacion,tipo,
        "(809) 555-0202","VENTAS@PROVEEDOR.COM",condicionId,monedaId,"Dirección","María","8095551234","Notas",true);}
    private Proveedor proveedor(boolean activo){Proveedor p=new Proveedor(tenant,empresa,"PRV-TEST","Proveedor",null,"131-45678-9","131456789",
        TipoProveedor.NATIONAL,null,null,condicionId,monedaId,null,null,null,null,activo,usuario);ReflectionTestUtils.setField(p,"id",UUID.randomUUID());return p;}
}
