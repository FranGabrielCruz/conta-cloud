package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.Cliente;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ClienteRepository extends JpaRepository<Cliente,UUID> {
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
    boolean existsByTenantIdAndEmpresaIdAndTipoIdentificacionAndIdentificacionNormalizada(UUID tenant,UUID empresa,
        com.citacloud.springboot.contacloud.app.models.TipoIdentificacionCliente tipo,String identificacion);
    boolean existsByTenantIdAndEmpresaIdAndTipoIdentificacionAndIdentificacionNormalizadaAndIdNot(UUID tenant,UUID empresa,
        com.citacloud.springboot.contacloud.app.models.TipoIdentificacionCliente tipo,String identificacion,UUID id);
}
