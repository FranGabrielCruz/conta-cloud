package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class RecuentoCajaConsultaService {
    private static final Set<Integer> PAGE_SIZES=Set.of(10,20,50);
    private static final UUID EMPTY_ID=new UUID(0L,0L);
    private final RecuentoCajaRepository recuentos;
    private final RecuentoCajaService detalle;
    private final CashRegisterRepository cajas;
    private final SucursalRepository sucursales;
    private final UsuarioRepository usuarios;
    private final EmpresaRepository empresas;

    public RecuentoCajaConsultaService(RecuentoCajaRepository recuentos,RecuentoCajaService detalle,
            CashRegisterRepository cajas,SucursalRepository sucursales,UsuarioRepository usuarios,
            EmpresaRepository empresas){
        this.recuentos=recuentos;this.detalle=detalle;this.cajas=cajas;this.sucursales=sucursales;
        this.usuarios=usuarios;this.empresas=empresas;
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('recuentos_caja.ver')")
    public RecuentoCajaCatalogosDto catalogos(){
        var p=TenantContext.principalActual();UUID empresa=EmpresaContext.requerirEmpresaId();
        List<Sucursal> ramas=(p.accesoTodasSucursales()?sucursales.findAllByEmpresaIdOrderByNombre(empresa)
            :sucursales.findAllByIdInAndEmpresaId(p.sucursalIds(),empresa).stream()
                .sorted(Comparator.comparing(Sucursal::getNombre)).toList());
        Set<UUID> ids=ramas.stream().map(Sucursal::getId).collect(java.util.stream.Collectors.toSet());
        var sucursalOpciones=ramas.stream().map(x->new RecuentoCajaCatalogosDto.SucursalOpcion(x.getId(),x.getNombre())).toList();
        var cajaOpciones=cajas.findAllByTenantIdAndEmpresaIdOrderByNombre(p.tenantId(),empresa).stream()
            .filter(x->ids.contains(x.getSucursalId()))
            .map(x->new RecuentoCajaCatalogosDto.CajaOpcion(x.getId(),x.getSucursalId(),x.getNombre())).toList();
        var usuarioOpciones=usuarios.findAllByTenantIdAndEmpresaIdOrderByNombreAscApellidoAsc(p.tenantId(),empresa).stream()
            .map(x->new RecuentoCajaCatalogosDto.UsuarioOpcion(x.getId(),etiqueta(x))).toList();
        return new RecuentoCajaCatalogosDto(sucursalOpciones,cajaOpciones,usuarioOpciones);
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('recuentos_caja.ver')")
    public Page<RecuentoCajaDto> buscar(String buscar,UUID sucursal,UUID caja,UUID usuario,LocalDate desde,
            LocalDate hasta,ResultadoRecuentoCaja resultado,int pagina,int tamano){
        validarPagina(pagina,tamano);
        if(desde!=null&&hasta!=null&&desde.isAfter(hasta))
            throw new ReglaNegocioException("La fecha desde no puede ser posterior a la fecha hasta.");
        var p=TenantContext.principalActual();UUID empresa=EmpresaContext.requerirEmpresaId();
        if(sucursal!=null&&!EmpresaContext.permiteSucursal(sucursal))
            throw new RecursoNoEncontradoException("Sucursal no encontrada.");
        if(caja!=null){Caja c=cajas.findByIdAndTenantIdAndEmpresaId(caja,p.tenantId(),empresa)
            .orElseThrow(()->new RecursoNoEncontradoException("Caja no encontrada."));
            if(!EmpresaContext.permiteSucursal(c.getSucursalId())||(sucursal!=null&&!sucursal.equals(c.getSucursalId())))
                throw new RecursoNoEncontradoException("Caja no encontrada.");}
        if(usuario!=null){Usuario u=usuarios.findByIdAndTenantId(usuario,p.tenantId())
            .filter(x->empresa.equals(x.getEmpresaId())).orElseThrow(()->new RecursoNoEncontradoException("Usuario no encontrado."));}
        ZoneId zona=zonaEmpresa(empresa,p.tenantId());
        OffsetDateTime inicio=desde==null?null:desde.atStartOfDay(zona).toOffsetDateTime();
        OffsetDateTime fin=hasta==null?null:hasta.plusDays(1).atStartOfDay(zona).toOffsetDateTime();
        Collection<UUID> ramas=p.sucursalIds().isEmpty()?List.of(EMPTY_ID):p.sucursalIds();
        return recuentos.buscarAdministrativo(p.tenantId(),empresa,p.accesoTodasSucursales(),ramas,
            limpiar(buscar),sucursal!=null,valorUuid(sucursal),caja!=null,valorUuid(caja),
            usuario!=null,valorUuid(usuario),inicio!=null,valorFecha(inicio),fin!=null,valorFecha(fin),
            resultado!=null,resultado==null?ResultadoRecuentoCaja.BALANCED:resultado,
            PageRequest.of(pagina,tamano,Sort.by(Sort.Order.desc("contadoEn"),Sort.Order.desc("id"))))
            .map(RecuentoCajaConsultaService::mapear);
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('CAJA_BANCOS') and hasAuthority('recuentos_caja.ver')")
    public RecuentoCajaDto obtener(UUID id){return detalle.obtener(id);}

    private ZoneId zonaEmpresa(UUID empresa,UUID tenant){String zona=empresas.findByIdAndTenantId(empresa,tenant)
        .map(Empresa::getZonaHoraria).orElse("America/Santo_Domingo");
        try{return ZoneId.of(zona);}catch(DateTimeException ex){return ZoneId.of("America/Santo_Domingo");}}
    private static RecuentoCajaDto mapear(RecuentoCaja r){return new RecuentoCajaDto(r.getId(),r.getNumero(),
        r.getSesionId(),r.getSesion()==null?null:r.getSesion().getCodigoVisible(),r.getCaja().getNombre(),
        r.getSucursal().getNombre(),r.getMoneda().getCodigoIso(),r.getMoneda().getSimbolo(),r.getContadoEn(),
        r.getUsuario().getUsuario(),r.getEsperado(),r.getContado(),r.getDiferencia(),r.getResult(),
        r.getObservation(),List.of());}
    private static String etiqueta(Usuario u){String nombre=(u.getNombre()+" "+u.getApellido()).trim();
        return nombre.isBlank()?u.getUsuario():u.getUsuario()+" · "+nombre;}
    private static String limpiar(String valor){return valor==null?"":valor.trim();}
    private static UUID valorUuid(UUID valor){return valor==null?EMPTY_ID:valor;}
    private static OffsetDateTime valorFecha(OffsetDateTime valor){return valor==null?OffsetDateTime.ofInstant(Instant.EPOCH,ZoneOffset.UTC):valor;}
    private static void validarPagina(int pagina,int tamano){if(pagina<0||!PAGE_SIZES.contains(tamano))
        throw new ReglaNegocioException("Paginación inválida.");}
}
