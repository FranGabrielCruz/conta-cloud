package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.ComprobanteFiscalMapper;
import com.citacloud.springboot.contacloud.app.models.TipoComprobanteFiscal;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ComprobanteFiscalService {
    private final TipoComprobanteFiscalRepository repository;
    private final SecuenciaFiscalRepository secuencias;
    private final ComprobanteFiscalMapper mapper;
    private final AuditoriaService auditoria;

    public ComprobanteFiscalService(TipoComprobanteFiscalRepository repository,SecuenciaFiscalRepository secuencias,
                                    ComprobanteFiscalMapper mapper,AuditoriaService auditoria){
        this.repository=repository;this.secuencias=secuencias;this.mapper=mapper;this.auditoria=auditoria;
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPROBANTES_FISCALES') and hasAuthority('comprobantes_fiscales.ver')")
    public Page<ComprobanteFiscalDto> buscar(String buscar,Boolean activo,int pagina,int tamano){
        return repository.buscar(TenantContext.requerirTenantId(),EmpresaContext.requerirEmpresaId(),texto(buscar),activo,
            PageRequest.of(pagina,tamano)).map(mapper::toDto);
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPROBANTES_FISCALES') and hasAuthority('comprobantes_fiscales.ver')")
    public ComprobanteFiscalDto obtener(UUID id){return mapper.toDto(seguro(id));}

    @Transactional(readOnly=true)
    @PreAuthorize("@empresaModuloService.habilitado('COMPROBANTES_FISCALES') and hasAnyAuthority('comprobantes_fiscales.ver','secuencias.crear','secuencias.editar')")
    public List<ComprobanteFiscalDto> activos(){
        return repository.findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByCodigo(TenantContext.requerirTenantId(),
            EmpresaContext.requerirEmpresaId()).stream().map(mapper::toDto).toList();
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('COMPROBANTES_FISCALES') and hasAuthority('comprobantes_fiscales.crear')")
    public ComprobanteFiscalDto crear(ComprobanteFiscalInput entrada){
        ComprobanteFiscalInput normalizado=validar(entrada);UUID tenantId=TenantContext.requerirTenantId(),empresaId=EmpresaContext.requerirEmpresaId();
        if(repository.existsByTenantIdAndEmpresaIdAndCodigoIgnoreCase(tenantId,empresaId,normalizado.codigo()))
            throw new ReglaNegocioException("Ya existe un comprobante con ese código.");
        TipoComprobanteFiscal entity=repository.save(mapper.toEntity(normalizado,tenantId,empresaId));
        auditoria.registrar("FISCAL_RECEIPT_TYPE_CREATED","TipoComprobanteFiscal",entity.getId(),"{}");
        return mapper.toDto(entity);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('COMPROBANTES_FISCALES') and hasAuthority('comprobantes_fiscales.editar')")
    public ComprobanteFiscalDto actualizar(UUID id,ComprobanteFiscalInput entrada){
        ComprobanteFiscalInput normalizado=validar(entrada);UUID tenantId=TenantContext.requerirTenantId(),empresaId=EmpresaContext.requerirEmpresaId();
        if(repository.existsByTenantIdAndEmpresaIdAndCodigoIgnoreCaseAndIdNot(tenantId,empresaId,normalizado.codigo(),id))
            throw new ReglaNegocioException("Ya existe un comprobante con ese código.");
        TipoComprobanteFiscal entity=seguro(id);String prefijoAnterior=entity.getPrefijo();
        if(!prefijoAnterior.equals(normalizado.prefijo())&&secuencias.tieneNumeracionUtilizada(tenantId,empresaId,id))
            throw new ReglaNegocioException("El prefijo no puede cambiarse porque el comprobante ya tiene numeración utilizada.");
        mapper.update(entity,normalizado);
        auditoria.registrar("FISCAL_RECEIPT_TYPE_UPDATED","TipoComprobanteFiscal",id,"{}");
        if(!prefijoAnterior.equals(normalizado.prefijo()))
            auditoria.registrar("FISCAL_RECEIPT_PREFIX_CHANGED","TipoComprobanteFiscal",id,
                "{\"anterior\":\""+prefijoAnterior+"\",\"nuevo\":\""+normalizado.prefijo()+"\"}");
        return mapper.toDto(entity);
    }

    @Transactional
    @PreAuthorize("@empresaModuloService.habilitado('COMPROBANTES_FISCALES') and hasAuthority('comprobantes_fiscales.desactivar')")
    public void cambiarEstado(UUID id,boolean activo){
        TipoComprobanteFiscal entity=seguro(id);entity.setActivo(activo);
        auditoria.registrar(activo?"FISCAL_RECEIPT_TYPE_ENABLED":"FISCAL_RECEIPT_TYPE_DISABLED","TipoComprobanteFiscal",id,"{}");
    }

    private TipoComprobanteFiscal seguro(UUID id){
        return repository.findByIdAndTenantIdAndEmpresaId(id,TenantContext.requerirTenantId(),EmpresaContext.requerirEmpresaId())
            .orElseThrow(()->new RecursoNoEncontradoException("Comprobante fiscal no encontrado."));
    }
    static ComprobanteFiscalInput validar(ComprobanteFiscalInput entrada){
        if(entrada==null||entrada.codigo()==null||entrada.codigo().isBlank())throw new ReglaNegocioException("El código es obligatorio.");
        if(entrada.nombre()==null||entrada.nombre().isBlank())throw new ReglaNegocioException("El nombre es obligatorio.");
        String codigo=normalizar(entrada.codigo(),30,"El código contiene caracteres no permitidos.");
        if(entrada.prefijo()==null||entrada.prefijo().isBlank())throw new ReglaNegocioException("El prefijo es obligatorio.");
        String prefijo=normalizar(entrada.prefijo(),20,"El prefijo contiene caracteres no permitidos.");
        return new ComprobanteFiscalInput(codigo,entrada.nombre().trim(),prefijo,textoNulo(entrada.descripcion()));
    }
    private static String normalizar(String valor,int longitud,String mensaje){String v=valor.trim().toUpperCase(Locale.ROOT);if(v.length()>longitud||!v.matches("[A-Z0-9_-]+"))throw new ReglaNegocioException(mensaje);return v;}
    private static String texto(String valor){return valor==null?"":valor.trim();}
    private static String textoNulo(String valor){String v=texto(valor);return v.isEmpty()?null:v;}
}
