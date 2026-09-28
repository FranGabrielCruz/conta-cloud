package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.SecuenciaFiscalMapper;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SecuenciaFiscalAdminService {
    private final SecuenciaFiscalRepository repository;
    private final TipoComprobanteFiscalRepository comprobantes;
    private final SecuenciaFiscalMapper mapper;
    private final AuditoriaService auditoria;

    public SecuenciaFiscalAdminService(SecuenciaFiscalRepository repository,TipoComprobanteFiscalRepository comprobantes,
                                       SecuenciaFiscalMapper mapper,AuditoriaService auditoria){
        this.repository=repository;this.comprobantes=comprobantes;this.mapper=mapper;this.auditoria=auditoria;
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('SECUENCIAS') and hasAuthority('secuencias.ver')")
    public Page<SecuenciaFiscalDto> buscar(String buscar,Boolean activo,int pagina,int tamano){
        UUID tenantId=TenantContext.requerirTenantId(),empresaId=EmpresaContext.requerirEmpresaId();
        Page<SecuenciaFiscal> paginaResultado=repository.buscar(tenantId,empresaId,buscar==null?"":buscar.trim(),activo,PageRequest.of(pagina,tamano));
        Map<UUID,TipoComprobanteFiscal> catalogo=comprobantes.findAllByIdInAndTenantIdAndEmpresaId(
            paginaResultado.stream().map(SecuenciaFiscal::getComprobanteId).toList(),tenantId,empresaId).stream()
            .collect(Collectors.toMap(TipoComprobanteFiscal::getId,Function.identity()));
        return paginaResultado.map(s->mapper.toDto(s,catalogo.get(s.getComprobanteId())));
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('SECUENCIAS') and hasAuthority('secuencias.ver')")
    public SecuenciaFiscalDto obtener(UUID id){SecuenciaFiscal s=seguro(id);return mapper.toDto(s,comprobanteSeguro(s.getComprobanteId(),false));}

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('SECUENCIAS') and hasAuthority('secuencias.crear')")
    public SecuenciaFiscalDto crear(SecuenciaFiscalInput entrada){
        validar(entrada);UUID tenantId=TenantContext.requerirTenantId(),empresaId=EmpresaContext.requerirEmpresaId();
        TipoComprobanteFiscal comprobante=comprobanteSeguro(entrada.comprobanteId(),true);
        validarDisponibilidad(tenantId,empresaId,entrada.comprobanteId(),entrada.numeroInicial(),entrada.numeroFinal(),null,true);
        String codigo="SEC-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
        SecuenciaFiscal entity=repository.save(mapper.toEntity(entrada,tenantId,empresaId,codigo));
        auditoria.registrar("FISCAL_SEQUENCE_CREATED","Secuencia",entity.getId(),"{}");
        return mapper.toDto(entity,comprobante);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('SECUENCIAS') and hasAuthority('secuencias.editar')")
    public SecuenciaFiscalDto actualizar(UUID id,SecuenciaFiscalInput entrada){
        validar(entrada);SecuenciaFiscal entity=seguro(id);boolean utilizada=entity.utilizada();
        if(utilizada&&!Objects.equals(entrada.comprobanteId(),entity.getComprobanteId()))
            throw new ReglaNegocioException("El comprobante de una secuencia utilizada no puede cambiarse.");
        if(utilizada&&entrada.numeroInicial()!=entity.getNumeroInicial())
            throw new ReglaNegocioException("El número inicial de una secuencia utilizada no puede cambiarse.");
        if(entrada.numeroFinal()!=null&&entrada.numeroFinal()<entity.getNumeroActual())
            throw new ReglaNegocioException("El número final no puede ser menor que el número actual.");
        UUID tenantId=TenantContext.requerirTenantId(),empresaId=EmpresaContext.requerirEmpresaId();
        TipoComprobanteFiscal comprobante=comprobanteSeguro(entrada.comprobanteId(),true);
        validarDisponibilidad(tenantId,empresaId,entrada.comprobanteId(),entrada.numeroInicial(),entrada.numeroFinal(),id,entity.isActivo());
        if(!utilizada&&entrada.numeroInicial()!=entity.getNumeroInicial()){
            entity.setNumeroInicial(entrada.numeroInicial());entity.setNumeroActual(entrada.numeroInicial()-1);
        }
        entity.setComprobanteId(entrada.comprobanteId());entity.setNumeroFinal(entrada.numeroFinal());
        auditoria.registrar("FISCAL_SEQUENCE_UPDATED","Secuencia",id,"{}");
        return mapper.toDto(entity,comprobante);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('SECUENCIAS') and hasAuthority('secuencias.desactivar')")
    public void cambiarEstado(UUID id,boolean activo){
        SecuenciaFiscal entity=seguro(id);UUID tenantId=TenantContext.requerirTenantId(),empresaId=EmpresaContext.requerirEmpresaId();
        if(activo){
            if(entity.agotada())throw new ReglaNegocioException("La secuencia está agotada y no puede reactivarse.");
            validarDisponibilidad(tenantId,empresaId,entity.getComprobanteId(),entity.getNumeroInicial(),entity.getNumeroFinal(),id,true);
        }
        entity.setActivo(activo);
        auditoria.registrar(activo?"FISCAL_SEQUENCE_ENABLED":"FISCAL_SEQUENCE_DISABLED","Secuencia",id,"{}");
    }

    private void validarDisponibilidad(UUID tenantId,UUID empresaId,UUID comprobanteId,long inicial,Long fin,UUID excluirId,boolean activa){
        boolean otraActiva=excluirId==null
            ?repository.existsByTenantIdAndEmpresaIdAndComprobanteIdAndActivoTrue(tenantId,empresaId,comprobanteId)
            :repository.existsByTenantIdAndEmpresaIdAndComprobanteIdAndActivoTrueAndIdNot(tenantId,empresaId,comprobanteId,excluirId);
        if(activa&&otraActiva)throw new ReglaNegocioException("Ya existe una secuencia activa para ese comprobante fiscal.");
        if(repository.existeSolapamiento(tenantId,empresaId,comprobanteId,inicial,fin,excluirId))
            throw new ReglaNegocioException("El rango se solapa con otra secuencia del comprobante.");
    }
    private SecuenciaFiscal seguro(UUID id){return repository.findByIdAndTenantIdAndEmpresaId(id,TenantContext.requerirTenantId(),EmpresaContext.requerirEmpresaId()).orElseThrow(()->new RecursoNoEncontradoException("Secuencia no encontrada."));}
    private TipoComprobanteFiscal comprobanteSeguro(UUID id,boolean exigirActivo){
        TipoComprobanteFiscal c=comprobantes.findByIdAndTenantIdAndEmpresaId(id,TenantContext.requerirTenantId(),EmpresaContext.requerirEmpresaId()).orElseThrow(()->new ReglaNegocioException("Seleccione un comprobante fiscal válido."));
        if(exigirActivo&&!c.isActivo())throw new ReglaNegocioException("El comprobante seleccionado está inactivo.");return c;
    }
    static void validar(SecuenciaFiscalInput entrada){
        if(entrada==null||entrada.comprobanteId()==null)throw new ReglaNegocioException("El comprobante fiscal es obligatorio.");
        if(entrada.numeroInicial()<1)throw new ReglaNegocioException("El número inicial debe ser mayor que cero.");
        if(entrada.numeroFinal()!=null&&entrada.numeroFinal()<entrada.numeroInicial())throw new ReglaNegocioException("El número final no puede ser menor que el inicial.");
    }
}
