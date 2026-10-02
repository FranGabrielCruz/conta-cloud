package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;
public interface CashRegisterSessionRepository extends JpaRepository<SesionCaja,UUID>{
    Optional<SesionCaja> findByTenantIdAndEmpresaIdAndCajaIdAndStatus(UUID tenant,UUID empresa,UUID caja,EstadoSesionCaja estado);
    Optional<SesionCaja> findByTenantIdAndEmpresaIdAndAbiertoPorAndStatus(UUID tenant,UUID empresa,UUID usuario,EstadoSesionCaja estado);
    List<SesionCaja> findAllByTenantIdAndEmpresaIdAndCajaIdInAndStatus(UUID tenant,UUID empresa,Collection<UUID> cajas,EstadoSesionCaja estado);
    @Query("select coalesce(max(s.numeroTurno),0) from SesionCaja s where s.tenantId=:tenant and s.empresaId=:empresa and s.cajaId=:caja and s.fechaOperativa=:fecha")
    int ultimoTurno(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("caja")UUID caja,@Param("fecha")LocalDate fecha);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SesionCaja s where s.id=:id and s.tenantId=:tenant and s.empresaId=:empresa")
    Optional<SesionCaja> bloquear(@Param("id")UUID id,@Param("tenant")UUID tenant,@Param("empresa")UUID empresa);
    @EntityGraph(attributePaths={"caja","sucursal","moneda","usuarioApertura","usuarioCierre","usuarioRevision"})
    Optional<SesionCaja> findByIdAndTenantIdAndEmpresaId(UUID id,UUID tenant,UUID empresa);
    @EntityGraph(attributePaths={"caja","sucursal","moneda","usuarioApertura","usuarioCierre","usuarioRevision"})
    @Query("""
      select s from SesionCaja s where s.tenantId=:tenant and s.empresaId=:empresa
      and (:buscar='' or lower(s.codigoVisible) like lower(concat('%',:buscar,'%'))
        or lower(s.caja.nombre) like lower(concat('%',:buscar,'%'))
        or lower(s.usuarioApertura.nombre) like lower(concat('%',:buscar,'%')))
      and (:desde is null or s.fechaOperativa>=:desde) and (:hasta is null or s.fechaOperativa<=:hasta)
      and (:caja is null or s.cajaId=:caja) and (:revision is null or s.estadoRevision=:revision)
      and s.status=com.citacloud.springboot.contacloud.app.models.EstadoSesionCaja.CLOSED
      and (:todas=true or s.sucursalId in :sucursales)
      """)
    Page<SesionCaja> buscarHistorial(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("buscar")String buscar,
        @Param("desde")LocalDate desde,@Param("hasta")LocalDate hasta,@Param("caja")UUID caja,
        @Param("revision")EstadoRevisionCaja revision,@Param("todas")boolean todas,@Param("sucursales")Set<UUID> sucursales,Pageable pageable);
}
