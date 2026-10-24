package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

public interface CotizacionRepository extends JpaRepository<Cotizacion,UUID> {
    @EntityGraph(attributePaths={"sucursal","moneda"})
    @Query("""
      select c from Cotizacion c where c.tenantId=:tenant and c.empresaId=:empresa
      and (:buscar='' or lower(c.numero) like lower(concat('%',:buscar,'%'))
        or lower(c.clienteNombre) like lower(concat('%',:buscar,'%'))
        or lower(coalesce(c.reference,'')) like lower(concat('%',:buscar,'%'))
        or exists(select l.id from LineaCotizacion l where l.cotizacion=c and
            (lower(l.productoCodigo) like lower(concat('%',:buscar,'%')) or lower(l.descripcion) like lower(concat('%',:buscar,'%')))))
      and (:desde is null or c.fecha>=:desde) and (:hasta is null or c.fecha<=:hasta)
      and (:estado is null or c.status=:estado) and (:cliente is null or c.clienteId=:cliente)
      and (:moneda is null or c.monedaId=:moneda)
      """)
    Page<Cotizacion> buscar(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("buscar")String buscar,
        @Param("desde")LocalDate desde,@Param("hasta")LocalDate hasta,@Param("estado")EstadoCotizacion estado,
        @Param("cliente")UUID cliente,@Param("moneda")UUID moneda,Pageable pageable);

    @EntityGraph(attributePaths={"sucursal","moneda"})
    @Query("""
      select c from Cotizacion c where c.tenantId=:tenant and c.empresaId=:empresa
      and c.status in :estados
      and (:buscar='' or lower(c.numero) like lower(concat('%',:buscar,'%'))
        or lower(c.clienteNombre) like lower(concat('%',:buscar,'%'))
        or lower(coalesce(c.clienteIdentificacion,'')) like lower(concat('%',:buscar,'%'))
        or replace(replace(coalesce(c.clienteIdentificacion,''),'-',''),' ','') like concat('%',:identificacion,'%'))
      and (:desde is null or c.fecha>=:desde) and (:hasta is null or c.fecha<=:hasta)
      and (:estado is null or c.status=:estado)
      """)
    Page<Cotizacion> buscarParaPos(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,
        @Param("buscar")String buscar,@Param("identificacion")String identificacion,
        @Param("desde")LocalDate desde,@Param("hasta")LocalDate hasta,@Param("estado")EstadoCotizacion estado,
        @Param("estados")Collection<EstadoCotizacion> estados,Pageable pageable);

    @EntityGraph(attributePaths={"lineas","sucursal","moneda"})
    Optional<Cotizacion> findByIdAndTenantIdAndEmpresaId(UUID id,UUID tenant,UUID empresa);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths={"lineas","sucursal","moneda"})
    @Query("select c from Cotizacion c where c.id=:id and c.tenantId=:tenant and c.empresaId=:empresa")
    Optional<Cotizacion> bloquear(@Param("id")UUID id,@Param("tenant")UUID tenant,@Param("empresa")UUID empresa);

    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("update Cotizacion c set c.status=com.citacloud.springboot.contacloud.app.models.EstadoCotizacion.VENCIDA, " +
        "c.actualizadaEn=:ahora, c.version=c.version+1 " +
        "where c.tenantId=:tenant and c.empresaId=:empresa and c.status in " +
        "(com.citacloud.springboot.contacloud.app.models.EstadoCotizacion.BORRADOR,com.citacloud.springboot.contacloud.app.models.EstadoCotizacion.ENVIADA) and c.validaHasta<:hoy")
    int marcarVencidas(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("hoy")LocalDate hoy,
        @Param("ahora")OffsetDateTime ahora);
}
