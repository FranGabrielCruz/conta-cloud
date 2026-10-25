package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.Cliente;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ClienteRepository extends JpaRepository<Cliente,UUID> {
    @Query(value="""
      select c from Cliente c where c.tenantId=:tenant and c.empresaId=:empresa and c.activo=true
      and (:buscar='' or lower(c.codigo) like lower(concat('%',:buscar,'%'))
        or lower(coalesce(c.nombres,'')) like lower(concat('%',:buscar,'%'))
        or lower(coalesce(c.apellidos,'')) like lower(concat('%',:buscar,'%'))
        or lower(coalesce(c.razonSocial,'')) like lower(concat('%',:buscar,'%'))
        or lower(coalesce(c.nombreComercial,'')) like lower(concat('%',:buscar,'%'))
        or lower(coalesce(c.identificacion,'')) like lower(concat('%',:buscar,'%'))
        or (:identificacion<>'' and lower(coalesce(c.identificacionNormalizada,'')) like lower(concat('%',:identificacion,'%')))
        or (:telefono<>'' and lower(coalesce(c.telefono,'')) like lower(concat('%',:telefono,'%')))
        or (:telefono<>'' and lower(coalesce(c.telefonoAlternativo,'')) like lower(concat('%',:telefono,'%'))))
      order by
        case
          when :buscar<>'' and lower(coalesce(c.identificacionNormalizada,''))=lower(:identificacion) then 0
          when :buscar<>'' and lower(c.codigo)=lower(:buscar) then 1
          when :buscar<>'' and (
            lower(trim(concat(coalesce(c.nombres,''),concat(' ',coalesce(c.apellidos,'')))))=lower(:buscar)
            or lower(coalesce(c.razonSocial,''))=lower(:buscar)
            or lower(coalesce(c.nombreComercial,''))=lower(:buscar)) then 2
          when :buscar<>'' and (
            lower(coalesce(c.nombres,'')) like lower(concat(:buscar,'%'))
            or lower(coalesce(c.apellidos,'')) like lower(concat(:buscar,'%'))
            or lower(coalesce(c.razonSocial,'')) like lower(concat(:buscar,'%'))
            or lower(coalesce(c.nombreComercial,'')) like lower(concat(:buscar,'%'))) then 3
          else 4
        end,
        lower(coalesce(c.nombreComercial,c.razonSocial,c.nombres,c.codigo)), lower(c.codigo)
      """,countQuery="""
      select count(c) from Cliente c where c.tenantId=:tenant and c.empresaId=:empresa and c.activo=true
      and (:buscar='' or lower(c.codigo) like lower(concat('%',:buscar,'%'))
        or lower(coalesce(c.nombres,'')) like lower(concat('%',:buscar,'%'))
        or lower(coalesce(c.apellidos,'')) like lower(concat('%',:buscar,'%'))
        or lower(coalesce(c.razonSocial,'')) like lower(concat('%',:buscar,'%'))
        or lower(coalesce(c.nombreComercial,'')) like lower(concat('%',:buscar,'%'))
        or lower(coalesce(c.identificacion,'')) like lower(concat('%',:buscar,'%'))
        or (:identificacion<>'' and lower(coalesce(c.identificacionNormalizada,'')) like lower(concat('%',:identificacion,'%')))
        or (:telefono<>'' and lower(coalesce(c.telefono,'')) like lower(concat('%',:telefono,'%')))
        or (:telefono<>'' and lower(coalesce(c.telefonoAlternativo,'')) like lower(concat('%',:telefono,'%'))))
      """)
    Page<Cliente> buscarParaPos(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,
        @Param("buscar")String buscar,@Param("identificacion")String identificacion,
        @Param("telefono")String telefono,Pageable page);

    @EntityGraph(attributePaths={"condicionPago","moneda","tipoComprobantePreferido"})
    @Query("""
      select c from Cliente c where c.tenantId=:tenant and c.empresaId=:empresa
      and (:activo is null or c.activo=:activo)
      and (:buscar='' or lower(c.codigo) like lower(concat('%',:buscar,'%'))
        or lower(coalesce(c.nombres,'')) like lower(concat('%',:buscar,'%'))
        or lower(coalesce(c.apellidos,'')) like lower(concat('%',:buscar,'%'))
        or lower(coalesce(c.razonSocial,'')) like lower(concat('%',:buscar,'%'))
        or lower(coalesce(c.nombreComercial,'')) like lower(concat('%',:buscar,'%'))
        or lower(coalesce(c.identificacion,'')) like lower(concat('%',:buscar,'%'))
        or (:identificacion<>'' and lower(coalesce(c.identificacionNormalizada,'')) like lower(concat('%',:identificacion,'%')))
        or (:telefono<>'' and lower(coalesce(c.telefono,'')) like lower(concat('%',:telefono,'%')))
        or (:telefono<>'' and lower(coalesce(c.telefonoAlternativo,'')) like lower(concat('%',:telefono,'%')))
        or lower(coalesce(c.correo,'')) like lower(concat('%',:buscar,'%')))
      """)
    Page<Cliente> buscar(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("buscar")String buscar,
        @Param("identificacion")String identificacion,@Param("telefono")String telefono,@Param("activo")Boolean activo,Pageable page);
    @EntityGraph(attributePaths={"condicionPago","moneda","tipoComprobantePreferido"})
    Optional<Cliente> findByIdAndTenantIdAndEmpresaId(UUID id,UUID tenant,UUID empresa);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths={"condicionPago","moneda"})
    @Query("select c from Cliente c where c.id=:id and c.tenantId=:tenant and c.empresaId=:empresa")
    Optional<Cliente> bloquear(@Param("id")UUID id,@Param("tenant")UUID tenant,@Param("empresa")UUID empresa);
    boolean existsByTenantIdAndEmpresaIdAndTipoIdentificacionAndIdentificacionNormalizada(UUID tenant,UUID empresa,
        com.citacloud.springboot.contacloud.app.models.TipoIdentificacionCliente tipo,String identificacion);
    boolean existsByTenantIdAndEmpresaIdAndTipoIdentificacionAndIdentificacionNormalizadaAndIdNot(UUID tenant,UUID empresa,
        com.citacloud.springboot.contacloud.app.models.TipoIdentificacionCliente tipo,String identificacion,UUID id);
}
