package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.ProveedorMapper;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class SupplierService {
    private static final Set<Integer> PAGE_SIZES=Set.of(10,25,50,100);
    private static final Pattern EMAIL=Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final SupplierRepository suppliers;
    private final CondicionPagoRepository condiciones;
    private final MonedaRepository monedas;
    private final ProveedorMapper mapper;
    private final AuditoriaService auditoria;

    public SupplierService(SupplierRepository suppliers,CondicionPagoRepository condiciones,MonedaRepository monedas,
            ProveedorMapper mapper,AuditoriaService auditoria){
        this.suppliers=suppliers;this.condiciones=condiciones;this.monedas=monedas;this.mapper=mapper;this.auditoria=auditoria;
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('proveedores.ver')")
    public Page<ProveedorDto> searchSuppliers(String buscar,Boolean activo,int pagina,int tamano,String ordenarPor,boolean asc){
        validarPagina(pagina,tamano);var p=TenantContext.principalActual();String texto=limpiar(buscar);
        String identificacion=texto.replaceAll("[^\\p{Alnum}]","");return suppliers.buscar(p.tenantId(),
            EmpresaContext.requerirEmpresaId(),texto,identificacion,activo,PageRequest.of(pagina,tamano,orden(ordenarPor,asc))).map(mapper::toDto);
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('proveedores.ver')")
    public ProveedorDto getSupplier(UUID id){return mapper.toDto(proveedorSeguro(id));}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAnyAuthority('proveedores.crear','proveedores.editar')")
    public ProveedorCatalogosDto catalogos(){var p=TenantContext.principalActual();UUID empresa=EmpresaContext.requerirEmpresaId();
        var terms=condiciones.findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(p.tenantId(),empresa).stream()
            .map(c->new ProveedorCatalogosDto.CondicionOpcion(c.getId(),nombreCondicion(c))).toList();
        var currencies=monedas.findAllByEmpresaIdAndActivoTrueOrderByCodigoIso(empresa).stream()
            .map(m->new ProveedorCatalogosDto.MonedaOpcion(m.getId(),m.getCodigoIso(),m.getNombre())).toList();
        return new ProveedorCatalogosDto(terms,currencies);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('proveedores.crear')")
    public ProveedorDto createSupplier(ProveedorInput input){var v=validar(input);var p=TenantContext.principalActual();
        UUID empresa=EmpresaContext.requerirEmpresaId();validarDuplicado(p.tenantId(),empresa,v.normalizada(),null);
        validarCatalogos(p.tenantId(),empresa,v.input().condicionPagoId(),v.input().monedaId());
        String codigo="PRV-"+UUID.randomUUID().toString().replace("-","").substring(0,20).toUpperCase(Locale.ROOT);
        Proveedor entity=mapper.toEntity(v.input(),p.tenantId(),empresa,codigo,v.identificacion(),v.normalizada(),
            v.telefono(),v.telefonoContacto(),p.usuarioId());
        try{entity=suppliers.saveAndFlush(entity);}catch(DataIntegrityViolationException ex){throw duplicado(ex);}
        auditoria.registrar("SUPPLIER_CREATED","Proveedor",entity.getId(),detalle(entity));return mapper.toDto(entity);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('proveedores.editar')")
    public ProveedorDto updateSupplier(UUID id,ProveedorInput input){Proveedor entity=proveedorSeguro(id);var v=validar(input);
        if(v.input().activo()!=entity.isActivo())throw new ReglaNegocioException("Utiliza la acción correspondiente para cambiar el estado del proveedor.");
        validarDuplicado(entity.getTenantId(),entity.getEmpresaId(),v.normalizada(),entity.getId());
        validarCatalogos(entity.getTenantId(),entity.getEmpresaId(),v.input().condicionPagoId(),v.input().monedaId());
        String anterior=detalle(entity);entity.actualizar(v.input().nombreComercial(),v.input().razonSocial(),v.identificacion(),
            v.normalizada(),v.input().tipo(),v.telefono(),v.input().correo(),v.input().condicionPagoId(),v.input().monedaId(),
            v.input().direccion(),v.input().contacto(),v.telefonoContacto(),v.input().notas(),TenantContext.principalActual().usuarioId());
        try{suppliers.saveAndFlush(entity);}catch(DataIntegrityViolationException ex){throw duplicado(ex);}
        auditoria.registrar("SUPPLIER_UPDATED","Proveedor",entity.getId(),"{\"anterior\":"+anterior+",\"nuevo\":"+detalle(entity)+"}");
        return mapper.toDto(entity);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('proveedores.desactivar')")
    public void deactivateSupplier(UUID id){cambiarEstado(proveedorSeguro(id),false,"SUPPLIER_DEACTIVATED");}

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('COMPRAS') and hasAuthority('proveedores.reactivar')")
    public void reactivateSupplier(UUID id){cambiarEstado(proveedorSeguro(id),true,"SUPPLIER_REACTIVATED");}

    private void cambiarEstado(Proveedor entity,boolean activo,String evento){if(entity.isActivo()==activo)
        throw new ReglaNegocioException(activo?"El proveedor ya está activo.":"El proveedor ya está inactivo.");
        entity.cambiarEstado(activo,TenantContext.principalActual().usuarioId());suppliers.saveAndFlush(entity);
        auditoria.registrar(evento,"Proveedor",entity.getId(),"{\"activo\":"+activo+"}");}

    private Validado validar(ProveedorInput input){if(input==null)throw new ReglaNegocioException("Los datos del proveedor son obligatorios.");
        String nombre=requerido(input.nombreComercial(),150,"El nombre comercial es obligatorio.","El nombre comercial excede 150 caracteres.");
        if(input.tipo()==null)throw new ReglaNegocioException("El tipo de proveedor es obligatorio.");
        String razon=opcional(input.razonSocial(),180,"La razón social excede 180 caracteres.");
        String identificacion=opcional(input.identificacionFiscal(),50,"La identificación excede 50 caracteres.");
        String normalizada=normalizarIdentificacion(identificacion,input.tipo());
        String correo=opcional(input.correo(),180,"El correo electrónico excede 180 caracteres.");
        if(correo!=null&&!EMAIL.matcher(correo).matches())throw new ReglaNegocioException("El correo electrónico no es válido.");
        correo=correo==null?null:correo.toLowerCase(Locale.ROOT);
        String telefono=normalizarTelefono(input.telefono(),"El teléfono excede 30 caracteres.");
        String contacto=opcional(input.contacto(),150,"El contacto excede 150 caracteres.");
        String telefonoContacto=normalizarTelefono(input.telefonoContacto(),"El teléfono del contacto excede 30 caracteres.");
        String direccion=opcional(input.direccion(),500,"La dirección excede 500 caracteres.");
        String notas=opcional(input.notas(),1000,"Las notas exceden 1000 caracteres.");
        var limpio=new ProveedorInput(nombre,razon,identificacion,input.tipo(),telefono,correo,input.condicionPagoId(),
            input.monedaId(),direccion,contacto,telefonoContacto,notas,input.activo());
        return new Validado(limpio,formatearIdentificacion(normalizada,input.tipo()),normalizada,telefono,telefonoContacto);
    }

    static String normalizarIdentificacion(String valor,TipoProveedor tipo){if(valor==null)return null;
        String normal=valor.replaceAll("[^\\p{Alnum}]","").toUpperCase(Locale.ROOT);
        if(tipo==TipoProveedor.NATIONAL&&!normal.matches("\\d{9}"))throw new ReglaNegocioException("El RNC no tiene un formato válido.");
        if(normal.isBlank())return null;return normal;}
    static String normalizarTelefono(String valor,String mensaje){String limpio=opcional(valor,30,mensaje);if(limpio==null)return null;
        boolean mas=limpio.startsWith("+");String digitos=limpio.replaceAll("\\D","");
        if(digitos.length()<7||digitos.length()>15)throw new ReglaNegocioException("El teléfono no tiene un formato válido.");
        return mas?"+"+digitos:digitos;}
    private static String formatearIdentificacion(String normal,TipoProveedor tipo){if(normal==null)return null;
        return tipo==TipoProveedor.NATIONAL?normal.substring(0,3)+"-"+normal.substring(3,8)+"-"+normal.substring(8):normal;}
    private void validarCatalogos(UUID tenant,UUID empresa,UUID condicionId,UUID monedaId){if(condicionId!=null){CondicionPago c=condiciones
        .findByIdAndTenantIdAndEmpresaId(condicionId,tenant,empresa).orElseThrow(()->new ReglaNegocioException("La condición de pago seleccionada no está disponible."));
        if(!c.isActivo())throw new ReglaNegocioException("La condición de pago seleccionada no está disponible.");}
        if(monedaId!=null){Moneda m=monedas.findByIdAndEmpresaId(monedaId,empresa).orElseThrow(()->new ReglaNegocioException("La moneda seleccionada no está disponible."));
        if(!m.isActivo())throw new ReglaNegocioException("La moneda seleccionada no está disponible.");}}
    private void validarDuplicado(UUID tenant,UUID empresa,String normal,UUID id){if(normal==null)return;boolean existe=id==null
        ?suppliers.existsByTenantIdAndEmpresaIdAndIdentificacionFiscalNormalizada(tenant,empresa,normal)
        :suppliers.existsByTenantIdAndEmpresaIdAndIdentificacionFiscalNormalizadaAndIdNot(tenant,empresa,normal,id);
        if(existe)throw new ReglaNegocioException("Ya existe un proveedor con este RNC o identificación.");}
    private Proveedor proveedorSeguro(UUID id){if(id==null)throw new RecursoNoEncontradoException("Proveedor no encontrado.");
        return suppliers.findByIdAndTenantIdAndEmpresaId(id,TenantContext.requerirTenantId(),EmpresaContext.requerirEmpresaId())
            .orElseThrow(()->new RecursoNoEncontradoException("Proveedor no encontrado."));}
    private static Sort orden(String campo,boolean asc){String p=switch(campo==null?"nombre":campo){case"identificacion"->"identificacionFiscal";case"telefono"->"telefono";case"correo"->"correo";case"estado"->"activo";default->"nombreComercial";};
        return Sort.by(asc?Sort.Direction.ASC:Sort.Direction.DESC,p).and(Sort.by("nombreComercial"));}
    private static void validarPagina(int pagina,int tamano){if(pagina<0||!PAGE_SIZES.contains(tamano))throw new ReglaNegocioException("Paginación inválida.");}
    private static String requerido(String v,int max,String vacio,String largo){String s=limpiar(v);if(s.isEmpty())throw new ReglaNegocioException(vacio);if(s.length()>max)throw new ReglaNegocioException(largo);return s;}
    private static String opcional(String v,int max,String largo){String s=limpiar(v);if(s.isEmpty())return null;if(s.length()>max)throw new ReglaNegocioException(largo);return s;}
    private static String limpiar(String v){return v==null?"":v.trim();}
    static String nombreCondicion(CondicionPago condicion){String nombre=limpiar(condicion.getNombre());
        return condicion.getTipo()==TipoCondicionPago.CREDIT&&Set.of("fiao","fíao","fia","fiado").contains(nombre.toLowerCase(Locale.ROOT))?"Crédito":nombre;}
    private static ReglaNegocioException duplicado(Exception ex){return new ReglaNegocioException("Ya existe un proveedor con este RNC o identificación.",ex);}
    private static String detalle(Proveedor p){return "{\"nombre\":\""+escapar(p.getNombreComercial())+"\",\"tipo\":\""+p.getTipo()+"\",\"activo\":"+p.isActivo()+"}";}
    private static String escapar(String v){return v.replace("\\","\\\\").replace("\"","\\\"");}
    private record Validado(ProveedorInput input,String identificacion,String normalizada,String telefono,String telefonoContacto){}
}
