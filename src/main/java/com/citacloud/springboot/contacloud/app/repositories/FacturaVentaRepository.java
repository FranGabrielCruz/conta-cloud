package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;
public interface FacturaVentaRepository extends JpaRepository<FacturaVenta,UUID>{
    Optional<FacturaVenta> findByTenantIdAndEmpresaIdAndClaveIdempotencia(UUID tenant,UUID empresa,UUID clave);
    Optional<FacturaVenta> findByIdAndTenantIdAndEmpresaId(UUID id,UUID tenant,UUID empresa);

    @Query(value="""
        SELECT f FROM FacturaVenta f
        WHERE f.tenantId=:tenant AND f.empresaId=:empresa
          AND (:todasSucursales=true OR f.sucursalId IN (:sucursales))
          AND (:desde IS NULL OR f.fecha>=:desde)
          AND (:hasta IS NULL OR f.fecha<=:hasta)
          AND (:estado IS NULL OR f.status=:estado)
          AND (:texto='' OR lower(f.numero) LIKE concat('%',:texto,'%')
               OR lower(f.clienteNombre) LIKE concat('%',:texto,'%')
               OR (:identificacion<>'' AND f.clienteIdentificacionNormalizada=:identificacion))
        ORDER BY CASE WHEN lower(f.numero)=:texto THEN 0 ELSE 1 END,
                 f.confirmadaEn DESC,f.id DESC
        """,countQuery="""
        SELECT count(f) FROM FacturaVenta f
        WHERE f.tenantId=:tenant AND f.empresaId=:empresa
          AND (:todasSucursales=true OR f.sucursalId IN (:sucursales))
          AND (:desde IS NULL OR f.fecha>=:desde)
          AND (:hasta IS NULL OR f.fecha<=:hasta)
          AND (:estado IS NULL OR f.status=:estado)
          AND (:texto='' OR lower(f.numero) LIKE concat('%',:texto,'%')
               OR lower(f.clienteNombre) LIKE concat('%',:texto,'%')
               OR (:identificacion<>'' AND f.clienteIdentificacionNormalizada=:identificacion))
        """)
    Page<FacturaVenta> buscarHistorico(@Param("tenant") UUID tenant,@Param("empresa") UUID empresa,
        @Param("todasSucursales") boolean todasSucursales,@Param("sucursales") Collection<UUID> sucursales,
        @Param("texto") String texto,@Param("identificacion") String identificacion,
        @Param("desde") LocalDate desde,@Param("hasta") LocalDate hasta,@Param("estado") EstadoFacturaVenta estado,Pageable pageable);
}
