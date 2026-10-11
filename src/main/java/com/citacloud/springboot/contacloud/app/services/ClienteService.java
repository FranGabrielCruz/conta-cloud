package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.ClienteMapper;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class ClienteService {
    private static final Set<Integer> PAGE_SIZES=Set.of(10,25,50,100);
    private static final Pattern EMAIL=Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final ClienteRepository clientes;private final DireccionClienteRepository direcciones;private final ContactoClienteRepository contactos;
    private final CondicionPagoRepository condiciones;private final MonedaRepository monedas;private final TipoComprobanteFiscalRepository comprobantes;
    private final ClienteMapper mapper;private final ClienteNumeroService numeros;private final AuditoriaService auditoria;
    public ClienteService(ClienteRepository clientes,DireccionClienteRepository direcciones,ContactoClienteRepository contactos,
            CondicionPagoRepository condiciones,MonedaRepository monedas,TipoComprobanteFiscalRepository comprobantes,
            ClienteMapper mapper,ClienteNumeroService numeros,AuditoriaService auditoria){this.clientes=clientes;this.direcciones=direcciones;
        this.contactos=contactos;this.condiciones=condiciones;this.monedas=monedas;this.comprobantes=comprobantes;
        this.mapper=mapper;this.numeros=numeros;this.auditoria=auditoria;}

    @Transactional(readOnly=true) @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('clientes.ver')")
    public Page<ClienteDto> buscar(String texto,Boolean activo,int pagina,int tamano){if(pagina<0||!PAGE_SIZES.contains(tamano))throw new ReglaNegocioException("Paginación inválida.");
        var p=TenantContext.principalActual();String q=clean(texto),normalized=q.replaceAll("[^\\p{Alnum}]","").toUpperCase(Locale.ROOT),phone=q.replaceAll("\\D","");
        Page<Cliente> page=clientes.buscar(p.tenantId(),p.empresaId(),q,normalized,phone,activo,PageRequest.of(pagina,tamano,Sort.by("razonSocial").ascending().and(Sort.by("nombres"))));
        Map<UUID,DireccionCliente> principals=page.isEmpty()?Map.of():direcciones.findAllByTenantIdAndEmpresaIdAndClienteIdInAndActiveTrueAndPrincipalTrue(p.tenantId(),p.empresaId(),page.map(Cliente::getId).getContent()).stream().collect(java.util.stream.Collectors.toMap(DireccionCliente::getClienteId,x->x));
        return new PageImpl<>(page.getContent().stream().map(c->mapper.toDto(c,principals.get(c.getId()))).toList(),page.getPageable(),page.getTotalElements());}
    @Transactional(readOnly=true) @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('clientes.ver')")
    public ClienteDto obtener(UUID id){return toDto(seguro(id));}
    @Transactional(readOnly=true) @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAnyAuthority('clientes.crear','clientes.editar')")
    public ClienteCatalogosDto catalogos(){var p=TenantContext.principalActual();var terms=condiciones.findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(p.tenantId(),p.empresaId()).stream()
        .map(c->new ClienteCatalogosDto.CondicionOpcion(c.getId(),nombreCondicion(c),c.getTipo(),c.getDias())).toList();
        var curr=monedas.findAllByEmpresaIdAndActivoTrueOrderByCodigoIso(p.empresaId()).stream().map(m->new ClienteCatalogosDto.MonedaOpcion(m.getId(),m.getCodigoIso(),m.getNombre(),m.getSimbolo())).toList();
        var docs=comprobantes.findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByCodigo(p.tenantId(),p.empresaId()).stream().map(c->new ClienteCatalogosDto.ComprobanteOpcion(c.getId(),c.getCodigo(),c.getNombre())).toList();
        UUID base=monedas.findByEmpresaIdAndMonedaBaseTrue(p.empresaId()).filter(Moneda::isActivo).map(Moneda::getId).orElse(null);return new ClienteCatalogosDto(terms,curr,docs,base);}

    @Transactional @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('clientes.crear')")
    public ClienteDto crear(ClienteInput input){var p=TenantContext.principalActual();Validado v=validar(input,null);validarDuplicado(p.tenantId(),p.empresaId(),v.tipoIdentificacion,v.normalizada,null);
        Cliente c=mapper.toEntity(v.input,p.tenantId(),p.empresaId(),numeros.siguiente(p.tenantId(),p.empresaId()),v.identificacion,v.normalizada,
            v.telefono,v.telefonoAlternativo,v.correo,v.diasCredito,v.limite,v.control,p.usuarioId());
        try{c=clientes.saveAndFlush(c);guardarDireccionInicial(c,v.input.direccionPrincipal(),p.usuarioId());}catch(DataIntegrityViolationException ex){throw duplicado(ex);}
        auditoria.registrar("CUSTOMER_CREATED","Cliente",c.getId(),detalle(c));return toDto(c);}
    @Transactional @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('clientes.editar')")
    public ClienteDto actualizar(UUID id,ClienteInput input){Cliente c=seguro(id);if(input!=null&&input.version()!=c.getVersion())throw concurrencia();Validado v=validar(input,c);
        validarDuplicado(c.getTenantId(),c.getEmpresaId(),v.tipoIdentificacion,v.normalizada,c.getId());String before=detalle(c);c.actualizar(v.input.tipoPersona(),v.input.nombres(),v.input.apellidos(),v.input.razonSocial(),v.input.nombreComercial(),v.tipoIdentificacion,
            v.identificacion,v.normalizada,v.telefono,v.telefonoAlternativo,v.correo,v.input.condicionPagoId(),v.input.monedaId(),v.diasCredito,
            v.limite,v.control,v.input.tipoComprobantePreferidoId(),v.input.tratamientoFiscal(),v.input.observacion(),TenantContext.principalActual().usuarioId());
        try{clientes.saveAndFlush(c);actualizarDireccionPrincipal(c,v.input.direccionPrincipal());}catch(ObjectOptimisticLockingFailureException ex){throw concurrencia();}catch(DataIntegrityViolationException ex){throw duplicado(ex);}
        auditoria.registrar("CUSTOMER_UPDATED","Cliente",c.getId(),"{\"anterior\":"+before+",\"nuevo\":"+detalle(c)+"}");return toDto(c);}
    @Transactional @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('clientes.desactivar')") public void desactivar(UUID id){cambiarEstado(seguro(id),false,"CUSTOMER_DEACTIVATED");}
    @Transactional @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('clientes.activar')") public void activar(UUID id){cambiarEstado(seguro(id),true,"CUSTOMER_ACTIVATED");}

    @Transactional(readOnly=true) @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('clientes.direcciones.ver')") public List<DireccionClienteDto> direcciones(UUID cliente){Cliente c=seguro(cliente);return direcciones.findAllByTenantIdAndEmpresaIdAndClienteIdAndActiveTrueOrderByPrincipalDescAddressAsc(c.getTenantId(),c.getEmpresaId(),c.getId()).stream().map(mapper::toDto).toList();}
    @Transactional @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('clientes.direcciones.administrar')") public DireccionClienteDto guardarDireccion(UUID cliente,UUID id,DireccionClienteInput input){Cliente c=seguro(cliente);DireccionValidada address=validarDireccion(input);var p=TenantContext.principalActual();DireccionCliente d=id==null
        ?new DireccionCliente(p.tenantId(),p.empresaId(),c.getId(),address.tipo,address.pais,address.provincia,address.ciudad,address.sector,address.direccion,address.codigoPostal,input.principal(),p.usuarioId())
        :direcciones.findByIdAndTenantIdAndEmpresaIdAndClienteId(id,p.tenantId(),p.empresaId(),c.getId()).orElseThrow(()->new RecursoNoEncontradoException("Dirección no encontrada."));
        if(id!=null){if(input.version()!=d.getVersion())throw concurrencia();d.actualizar(address.tipo,address.pais,address.provincia,address.ciudad,address.sector,address.direccion,address.codigoPostal,input.principal(),p.usuarioId());}
        if(input.principal())quitarPrincipal(c,d.getId(),p.usuarioId());d=direcciones.saveAndFlush(d);auditoria.registrar(id==null?"CUSTOMER_ADDRESS_CREATED":"CUSTOMER_ADDRESS_UPDATED","DireccionCliente",d.getId(),"{\"cliente\":\""+c.getId()+"\"}");return mapper.toDto(d);}
    @Transactional @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('clientes.direcciones.administrar')") public void desactivarDireccion(UUID cliente,UUID id){Cliente c=seguro(cliente);var p=TenantContext.principalActual();DireccionCliente d=direcciones.findByIdAndTenantIdAndEmpresaIdAndClienteId(id,p.tenantId(),p.empresaId(),c.getId()).orElseThrow(()->new RecursoNoEncontradoException("Dirección no encontrada."));d.desactivar(p.usuarioId());direcciones.saveAndFlush(d);auditoria.registrar("CUSTOMER_ADDRESS_DEACTIVATED","DireccionCliente",id,"{}");}
    @Transactional(readOnly=true) @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('clientes.contactos.ver')") public List<ContactoClienteDto> contactos(UUID cliente){Cliente c=seguro(cliente);return contactos.findAllByTenantIdAndEmpresaIdAndClienteIdAndActiveTrueOrderByPrincipalDescNombreAsc(c.getTenantId(),c.getEmpresaId(),c.getId()).stream().map(mapper::toDto).toList();}
    @Transactional @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('clientes.contactos.administrar')") public ContactoClienteDto guardarContacto(UUID cliente,UUID id,ContactoClienteInput input){Cliente c=seguro(cliente);var p=TenantContext.principalActual();String nombre=required(input==null?null:input.nombre(),150,"El nombre del contacto es obligatorio.");String email=email(input.correo());String phone=phone(input.telefono());ContactoCliente contact=id==null
        ?new ContactoCliente(p.tenantId(),p.empresaId(),c.getId(),nombre,optional(input.cargo(),100),phone,email,input.principal(),p.usuarioId())
        :contactos.findByIdAndTenantIdAndEmpresaIdAndClienteId(id,p.tenantId(),p.empresaId(),c.getId()).orElseThrow(()->new RecursoNoEncontradoException("Contacto no encontrado."));
        if(id!=null){if(input.version()!=contact.getVersion())throw concurrencia();contact.actualizar(nombre,optional(input.cargo(),100),phone,email,input.principal(),p.usuarioId());}
        contact=contactos.saveAndFlush(contact);auditoria.registrar(id==null?"CUSTOMER_CONTACT_CREATED":"CUSTOMER_CONTACT_UPDATED","ContactoCliente",contact.getId(),"{\"cliente\":\""+c.getId()+"\"}");return mapper.toDto(contact);}
    @Transactional @PreAuthorize("@empresaModuloService.habilitado('VENTAS') and hasAuthority('clientes.contactos.administrar')") public void desactivarContacto(UUID cliente,UUID id){Cliente c=seguro(cliente);var p=TenantContext.principalActual();ContactoCliente contact=contactos.findByIdAndTenantIdAndEmpresaIdAndClienteId(id,p.tenantId(),p.empresaId(),c.getId()).orElseThrow(()->new RecursoNoEncontradoException("Contacto no encontrado."));contact.desactivar(p.usuarioId());contactos.saveAndFlush(contact);auditoria.registrar("CUSTOMER_CONTACT_DEACTIVATED","ContactoCliente",id,"{}");}

    private Validado validar(ClienteInput i,Cliente actual){if(i==null)throw new ReglaNegocioException("Los datos del cliente son obligatorios.");if(i.tipoPersona()==null)throw new ReglaNegocioException("El tipo de persona es obligatorio.");
        String nombres=null,apellidos=null,razon=null;if(i.tipoPersona()==TipoPersonaCliente.FISICA){nombres=required(i.nombres(),120,"Los nombres son obligatorios.");apellidos=required(i.apellidos(),120,"Los apellidos son obligatorios.");}else razon=required(i.razonSocial(),180,"La razón social es obligatoria.");
        String trade=optional(i.nombreComercial(),150),identification=optional(i.identificacion(),50),normalized=normalizeIdentification(identification,i.tipoIdentificacion());
        if((identification==null)!=(i.tipoIdentificacion()==null))throw new ReglaNegocioException("El tipo y el número de identificación deben completarse juntos.");
        String mail=email(i.correo()),telephone=phone(i.telefono()),alternate=phone(i.telefonoAlternativo());var catalogs=validateCatalogs(i);
        int days=i.diasCredito()==null?0:i.diasCredito();BigDecimal limit=i.limiteCredito()==null?BigDecimal.ZERO:i.limiteCredito();ControlLimiteCredito control=i.controlLimite()==null?ControlLimiteCredito.NO_CONTROLAR:i.controlLimite();
        if(days<0||limit.signum()<0)throw new ReglaNegocioException("Los días y el límite de crédito no pueden ser negativos.");if(catalogs.term.getTipo()==TipoCondicionPago.CASH){days=0;limit=BigDecimal.ZERO;control=ControlLimiteCredito.NO_CONTROLAR;}
        else if(!TenantContext.principalActual().permisos().contains("clientes.credito.editar")&&(actual==null||days!=actual.getDiasCredito()||limit.compareTo(actual.getLimiteCredito())!=0||control!=actual.getControlLimite()))throw new ReglaNegocioException("No tienes permiso para editar las condiciones de crédito.");
        ClienteInput cleaned=new ClienteInput(i.tipoPersona(),nombres,apellidos,razon,trade,i.tipoIdentificacion(),identification,telephone,alternate,mail,
            i.condicionPagoId(),i.monedaId(),days,limit,control,i.tipoComprobantePreferidoId(),optional(i.tratamientoFiscal(),100),optional(i.observacion(),1000),i.direccionPrincipal(),i.version());
        return new Validado(cleaned,i.tipoIdentificacion(),formatIdentification(normalized,i.tipoIdentificacion()),normalized,telephone,alternate,mail,days,limit,control);}
    private Catalogs validateCatalogs(ClienteInput i){var p=TenantContext.principalActual();CondicionPago term=condiciones.findByIdAndTenantIdAndEmpresaId(i.condicionPagoId(),p.tenantId(),p.empresaId()).filter(CondicionPago::isActivo).orElseThrow(()->new ReglaNegocioException("La condición de pago es obligatoria o no está disponible."));
        monedas.findByIdAndEmpresaId(i.monedaId(),p.empresaId()).filter(Moneda::isActivo).orElseThrow(()->new ReglaNegocioException("La moneda preferida es obligatoria o no está disponible."));if(i.tipoComprobantePreferidoId()!=null)comprobantes.findByIdAndTenantIdAndEmpresaId(i.tipoComprobantePreferidoId(),p.tenantId(),p.empresaId()).filter(TipoComprobanteFiscal::isActivo).orElseThrow(()->new ReglaNegocioException("El tipo de comprobante no está disponible."));return new Catalogs(term);}
    private void validarDuplicado(UUID tenant,UUID empresa,TipoIdentificacionCliente type,String value,UUID id){if(type==null||value==null)return;boolean exists=id==null?clientes.existsByTenantIdAndEmpresaIdAndTipoIdentificacionAndIdentificacionNormalizada(tenant,empresa,type,value):clientes.existsByTenantIdAndEmpresaIdAndTipoIdentificacionAndIdentificacionNormalizadaAndIdNot(tenant,empresa,type,value,id);if(exists)throw new ReglaNegocioException("Ya existe un cliente con este tipo e identificación.");}
    private ClienteDto toDto(Cliente c){var principal=direcciones.findFirstByTenantIdAndEmpresaIdAndClienteIdAndActiveTrueAndPrincipalTrue(c.getTenantId(),c.getEmpresaId(),c.getId()).orElse(null);return mapper.toDto(c,principal);}
    private Cliente seguro(UUID id){if(id==null)throw new RecursoNoEncontradoException("Cliente no encontrado.");var p=TenantContext.principalActual();return clientes.findByIdAndTenantIdAndEmpresaId(id,p.tenantId(),p.empresaId()).orElseThrow(()->new RecursoNoEncontradoException("Cliente no encontrado."));}
    private void cambiarEstado(Cliente c,boolean active,String action){if(c.isActivo()==active)throw new ReglaNegocioException(active?"El cliente ya está activo.":"El cliente ya está inactivo.");c.cambiarEstado(active,TenantContext.principalActual().usuarioId());clientes.saveAndFlush(c);auditoria.registrar(action,"Cliente",c.getId(),"{\"activo\":"+active+"}");}
    private void guardarDireccionInicial(Cliente c,DireccionClienteInput i,UUID user){if(i==null||clean(i.direccion()).isEmpty())return;DireccionValidada a=validarDireccion(i);direcciones.save(new DireccionCliente(c.getTenantId(),c.getEmpresaId(),c.getId(),a.tipo,a.pais,a.provincia,a.ciudad,a.sector,a.direccion,a.codigoPostal,true,user));}
    private void actualizarDireccionPrincipal(Cliente c,DireccionClienteInput input){if(input==null||!TenantContext.principalActual().permisos().contains("clientes.direcciones.administrar"))return;DireccionValidada a=validarDireccion(input);UUID user=TenantContext.principalActual().usuarioId();DireccionCliente current=direcciones.findFirstByTenantIdAndEmpresaIdAndClienteIdAndActiveTrueAndPrincipalTrue(c.getTenantId(),c.getEmpresaId(),c.getId()).orElse(null);if(current==null){direcciones.save(new DireccionCliente(c.getTenantId(),c.getEmpresaId(),c.getId(),a.tipo,a.pais,a.provincia,a.ciudad,a.sector,a.direccion,a.codigoPostal,true,user));return;}if(input.version()!=current.getVersion())throw concurrencia();current.actualizar(a.tipo,a.pais,a.provincia,a.ciudad,a.sector,a.direccion,a.codigoPostal,true,user);direcciones.saveAndFlush(current);}
    private void quitarPrincipal(Cliente c,UUID except,UUID user){direcciones.findFirstByTenantIdAndEmpresaIdAndClienteIdAndActiveTrueAndPrincipalTrue(c.getTenantId(),c.getEmpresaId(),c.getId()).filter(x->!Objects.equals(x.getId(),except)).ifPresent(x->{x.setPrincipal(false,user);direcciones.saveAndFlush(x);});}
    private static DireccionValidada validarDireccion(DireccionClienteInput i){if(i==null)throw new ReglaNegocioException("Los datos de la dirección son obligatorios.");return new DireccionValidada(i.tipo()==null?TipoDireccionCliente.OTRA:i.tipo(),required(i.pais(),100,"El país es obligatorio."),optional(i.provincia(),100),optional(i.ciudad(),100),optional(i.sector(),100),required(i.direccion(),500,"La dirección es obligatoria."),optional(i.codigoPostal(),20));}
    static String normalizeIdentification(String value,TipoIdentificacionCliente type){if(value==null)return null;String normalized=value.replaceAll("[^\\p{Alnum}]","").toUpperCase(Locale.ROOT);if(normalized.isBlank())return null;if(type==TipoIdentificacionCliente.RNC&&!normalized.matches("\\d{9}"))throw new ReglaNegocioException("El RNC debe contener 9 dígitos.");if(type==TipoIdentificacionCliente.CEDULA&&!normalized.matches("\\d{11}"))throw new ReglaNegocioException("La cédula debe contener 11 dígitos.");return normalized;}
    static String formatIdentification(String value,TipoIdentificacionCliente type){if(value==null)return null;if(type==TipoIdentificacionCliente.RNC)return value.substring(0,3)+"-"+value.substring(3,8)+"-"+value.substring(8);if(type==TipoIdentificacionCliente.CEDULA)return value.substring(0,3)+"-"+value.substring(3,10)+"-"+value.substring(10);return value;}
    static String phone(String value){String v=optional(value,30);if(v==null)return null;boolean plus=v.startsWith("+");String digits=v.replaceAll("\\D","");if(digits.length()<7||digits.length()>15)throw new ReglaNegocioException("El teléfono no tiene un formato válido.");return plus?"+"+digits:digits;}
    private static String email(String value){String v=optional(value,180);if(v!=null&&!EMAIL.matcher(v).matches())throw new ReglaNegocioException("El correo electrónico no es válido.");return v==null?null:v.toLowerCase(Locale.ROOT);}
    private static String required(String value,int max,String message){String v=clean(value);if(v.isEmpty())throw new ReglaNegocioException(message);if(v.length()>max)throw new ReglaNegocioException("El valor excede "+max+" caracteres.");return v;}
    private static String optional(String value,int max){String v=clean(value);if(v.isEmpty())return null;if(v.length()>max)throw new ReglaNegocioException("El valor excede "+max+" caracteres.");return v;}
    private static String clean(String value){return value==null?"":value.trim();}private static String nombreCondicion(CondicionPago c){return c.getTipo()==TipoCondicionPago.CREDIT?"Crédito":c.getNombre();}
    private static ReglaNegocioException duplicado(Exception ex){return new ReglaNegocioException("Ya existe un cliente con este tipo e identificación.",ex);}private static ReglaNegocioException concurrencia(){return new ReglaNegocioException("El cliente fue modificado por otro usuario. Actualiza la pantalla e inténtalo nuevamente.");}
    private static String detalle(Cliente c){String name=c.getTipoPersona()==TipoPersonaCliente.FISICA?clean(c.getNombres()+" "+c.getApellidos()):clean(c.getRazonSocial());return "{\"codigo\":"+json(c.getCodigo())+",\"nombre\":"+json(name)+",\"identificacion\":"+json(c.getIdentificacion())+",\"condicionPagoId\":"+json(String.valueOf(c.getCondicionPagoId()))+",\"diasCredito\":"+c.getDiasCredito()+",\"limiteCredito\":"+c.getLimiteCredito()+",\"controlLimite\":"+json(String.valueOf(c.getControlLimite()))+",\"tipoComprobanteId\":"+json(String.valueOf(c.getTipoComprobantePreferidoId()))+",\"activo\":"+c.isActivo()+"}";}
    private static String json(String value){if(value==null||"null".equals(value))return "null";return "\""+value.replace("\\","\\\\").replace("\"","\\\"")+"\"";}
    private record Catalogs(CondicionPago term){}private record DireccionValidada(TipoDireccionCliente tipo,String pais,String provincia,String ciudad,String sector,String direccion,String codigoPostal){}private record Validado(ClienteInput input,TipoIdentificacionCliente tipoIdentificacion,String identificacion,String normalizada,String telefono,String telefonoAlternativo,String correo,int diasCredito,BigDecimal limite,ControlLimiteCredito control){}
}
