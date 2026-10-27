package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.*;import jakarta.persistence.LockModeType;import org.springframework.data.domain.*;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;import java.time.OffsetDateTime;import java.util.*;
public interface ValeCajaRepository extends JpaRepository<ValeCaja,UUID>{
 boolean existsByTenantIdAndEmpresaIdAndEmpresaLogoObjectKey(UUID tenant,UUID empresa,String key);
 Optional<ValeCaja> findByIdAndTenantIdAndEmpresaId(UUID id,UUID tenant,UUID empresa);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select v from ValeCaja v where v.id=:id and v.tenantId=:tenant and v.empresaId=:empresa")
 Optional<ValeCaja> bloquear(@Param("id")UUID id,@Param("tenant")UUID tenant,@Param("empresa")UUID empresa);
 @Query("""
  select v from ValeCaja v where v.tenantId=:tenant and v.empresaId=:empresa and v.sesionId=:sesion
   and (:buscar='' or lower(v.numero) like lower(concat('%',:buscar,'%')) or lower(v.beneficiario) like lower(concat('%',:buscar,'%'))
    or lower(v.concepto) like lower(concat('%',:buscar,'%')) or lower(coalesce(v.reference,'')) like lower(concat('%',:buscar,'%')))
   and (:estado is null or v.status=:estado)
  """)
 Page<ValeCaja> buscarPos(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("sesion")UUID sesion,
  @Param("buscar")String buscar,@Param("estado")EstadoValeCaja estado,Pageable pageable);
 @Query("""
  select v from ValeCaja v where v.tenantId=:tenant and v.empresaId=:empresa
   and (:buscar='' or lower(v.numero) like lower(concat('%',:buscar,'%')) or lower(v.beneficiario) like lower(concat('%',:buscar,'%'))
    or lower(v.concepto) like lower(concat('%',:buscar,'%')) or lower(coalesce(v.reference,'')) like lower(concat('%',:buscar,'%')))
   and (:filtrarSucursal=false or v.sucursalId=:sucursal) and (:filtrarCaja=false or v.cajaId=:caja)
   and (:filtrarUsuario=false or v.creadoPor=:usuario) and (:filtrarEstado=false or v.status=:estado)
   and (:filtrarDesde=false or v.fechaHora>=:desde) and (:filtrarHasta=false or v.fechaHora<:hasta)
   and (:todas=true or v.sucursalId in :sucursales)
  """)
 Page<ValeCaja> buscarAdmin(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("buscar")String buscar,
  @Param("filtrarSucursal")boolean filtrarSucursal,@Param("sucursal")UUID sucursal,
  @Param("filtrarCaja")boolean filtrarCaja,@Param("caja")UUID caja,
  @Param("filtrarUsuario")boolean filtrarUsuario,@Param("usuario")UUID usuario,
  @Param("filtrarEstado")boolean filtrarEstado,@Param("estado")EstadoValeCaja estado,
  @Param("filtrarDesde")boolean filtrarDesde,@Param("desde")OffsetDateTime desde,
  @Param("filtrarHasta")boolean filtrarHasta,@Param("hasta")OffsetDateTime hasta,@Param("todas")boolean todas,
  @Param("sucursales")Set<UUID> sucursales,Pageable pageable);
}
