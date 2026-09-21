package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.Moneda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface MonedaRepository extends JpaRepository<Moneda, UUID> {
    Optional<Moneda> findByEmpresaIdAndMonedaBaseTrue(UUID empresaId);
    Optional<Moneda> findByIdAndEmpresaId(UUID id, UUID empresaId);
    Optional<Moneda> findByEmpresaIdAndCodigoIsoIgnoreCase(UUID empresaId, String codigoIso);
    List<Moneda> findAllByEmpresaIdAndActivoTrueOrderByCodigoIso(UUID empresaId);
    boolean existsByEmpresaIdAndCodigoIsoIgnoreCase(UUID empresaId, String codigoIso);
    long countByEmpresaId(UUID empresaId);
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Moneda m set m.monedaBase=false where m.empresaId=:empresaId and m.monedaBase=true")
    int clearBase(@Param("empresaId") UUID empresaId);
    @Query("""
        select m from Moneda m where m.empresaId=:empresaId
          and (:buscar='' or lower(m.codigoIso) like lower(concat('%',:buscar,'%'))
            or lower(m.nombre) like lower(concat('%',:buscar,'%'))
            or lower(m.simbolo) like lower(concat('%',:buscar,'%')))
        """)
    Page<Moneda> buscar(@Param("empresaId") UUID empresaId, @Param("buscar") String buscar, Pageable pageable);
    @Query("""
        select m from Moneda m where m.empresaId=:empresaId and m.activo=:activo
          and (:buscar='' or lower(m.codigoIso) like lower(concat('%',:buscar,'%'))
            or lower(m.nombre) like lower(concat('%',:buscar,'%'))
            or lower(m.simbolo) like lower(concat('%',:buscar,'%')))
        """)
    Page<Moneda> buscarPorEstado(@Param("empresaId") UUID empresaId, @Param("buscar") String buscar,
                                 @Param("activo") boolean activo, Pageable pageable);
}
