package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.*; import org.springframework.data.domain.*; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import java.time.OffsetDateTime; import java.util.*;
public interface RecuentoCajaRepository extends JpaRepository<RecuentoCaja,UUID>{
 @EntityGraph(attributePaths={"caja","sucursal","moneda","usuario","sesion"}) Optional<RecuentoCaja> findByIdAndTenantIdAndEmpresaId(UUID id,UUID t,UUID e);
 @EntityGraph(attributePaths={"caja","sucursal","moneda","usuario","sesion"})
 @Query("select r from RecuentoCaja r where r.tenantId=:t and r.empresaId=:e and r.sesionId=:s") Page<RecuentoCaja> historial(@Param("t")UUID t,@Param("e")UUID e,@Param("s")UUID s,Pageable p);
 @EntityGraph(attributePaths={"caja","sucursal","moneda","usuario","sesion"})
 @Query("""
  select r from RecuentoCaja r join r.caja c join r.usuario u
  where r.tenantId=:tenant and r.empresaId=:empresa
    and (:todas=true or r.sucursalId in :sucursalesPermitidas)
    and (:filtrarSucursal=false or r.sucursalId=:sucursal)
    and (:filtrarCaja=false or r.cajaId=:caja)
    and (:filtrarUsuario=false or r.contadoPor=:usuario)
    and (:filtrarDesde=false or r.contadoEn>=:desde)
    and (:filtrarHasta=false or r.contadoEn<:hasta)
    and (:filtrarResultado=false or r.result=:resultado)
    and (:buscar='' or lower(r.numero) like lower(concat('%',:buscar,'%'))
      or lower(c.nombre) like lower(concat('%',:buscar,'%'))
      or lower(c.codigo) like lower(concat('%',:buscar,'%'))
      or lower(u.usuario) like lower(concat('%',:buscar,'%'))
      or lower(u.nombre) like lower(concat('%',:buscar,'%')))
  """)
 Page<RecuentoCaja> buscarAdministrativo(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,
   @Param("todas")boolean todas,@Param("sucursalesPermitidas")Collection<UUID> sucursalesPermitidas,
   @Param("buscar")String buscar,@Param("filtrarSucursal")boolean filtrarSucursal,@Param("sucursal")UUID sucursal,
   @Param("filtrarCaja")boolean filtrarCaja,@Param("caja")UUID caja,
   @Param("filtrarUsuario")boolean filtrarUsuario,@Param("usuario")UUID usuario,
   @Param("filtrarDesde")boolean filtrarDesde,@Param("desde")OffsetDateTime desde,
   @Param("filtrarHasta")boolean filtrarHasta,@Param("hasta")OffsetDateTime hasta,
   @Param("filtrarResultado")boolean filtrarResultado,@Param("resultado")ResultadoRecuentoCaja resultado,
   Pageable pageable);
}
