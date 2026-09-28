package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.PeriodoFiscal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
public interface PeriodoFiscalRepository extends JpaRepository<PeriodoFiscal, UUID> {
    Optional<PeriodoFiscal> findFirstByEmpresaIdAndFechaInicialLessThanEqualAndFechaFinalGreaterThanEqual(UUID empresaId, LocalDate inicio, LocalDate fin);
    Optional<PeriodoFiscal> findByIdAndTenantIdAndEmpresaId(UUID id, UUID tenantId, UUID empresaId);

    @Query("""
        select p from PeriodoFiscal p
         where p.tenantId=:tenantId and p.empresaId=:empresaId
           and (:buscar='' or lower(p.nombre) like lower(concat('%',:buscar,'%')))
           and (:anio is null or p.anio=:anio)
           and (:estado is null or p.estado=:estado)
         order by p.fechaInicial desc
        """)
    Page<PeriodoFiscal> buscar(@Param("tenantId") UUID tenantId,
                               @Param("empresaId") UUID empresaId,
                               @Param("buscar") String buscar,
                               @Param("anio") Integer anio,
                               @Param("estado") PeriodoFiscal.Estado estado,
                               Pageable pageable);

    @Query("""
        select case when count(p)>0 then true else false end from PeriodoFiscal p
         where p.tenantId=:tenantId and p.empresaId=:empresaId
           and (:excluirId is null or p.id<>:excluirId)
           and p.fechaInicial<=:fechaFinal and p.fechaFinal>=:fechaInicial
        """)
    boolean existeSolapamiento(@Param("tenantId") UUID tenantId,
                               @Param("empresaId") UUID empresaId,
                               @Param("excluirId") UUID excluirId,
                               @Param("fechaInicial") LocalDate fechaInicial,
                               @Param("fechaFinal") LocalDate fechaFinal);

    Optional<PeriodoFiscal> findFirstByTenantIdAndEmpresaIdAndFechaInicialLessThanEqualAndFechaFinalGreaterThanEqual(
        UUID tenantId, UUID empresaId, LocalDate fechaInicial, LocalDate fechaFinal);

    @Query("select distinct p.anio from PeriodoFiscal p where p.tenantId=:tenantId and p.empresaId=:empresaId order by p.anio desc")
    List<Integer> listarAnios(@Param("tenantId") UUID tenantId, @Param("empresaId") UUID empresaId);
}
