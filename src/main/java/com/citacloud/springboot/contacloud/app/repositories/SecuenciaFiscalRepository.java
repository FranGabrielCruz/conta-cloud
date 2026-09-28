package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.SecuenciaFiscal;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SecuenciaFiscalRepository extends JpaRepository<SecuenciaFiscal,UUID> {
    Optional<SecuenciaFiscal> findByIdAndTenantIdAndEmpresaId(UUID id,UUID tenantId,UUID empresaId);
    boolean existsByTenantIdAndEmpresaIdAndComprobanteIdAndActivoTrue(UUID tenantId,UUID empresaId,UUID comprobanteId);
    boolean existsByTenantIdAndEmpresaIdAndComprobanteIdAndActivoTrueAndIdNot(UUID tenantId,UUID empresaId,UUID comprobanteId,UUID id);

    @Query("select (count(s)>0) from SecuenciaFiscal s where s.tenantId=:tenantId and s.empresaId=:empresaId and s.comprobanteId=:comprobanteId and s.numeroActual>=s.numeroInicial")
    boolean tieneNumeracionUtilizada(@Param("tenantId")UUID tenantId,@Param("empresaId")UUID empresaId,
                                     @Param("comprobanteId")UUID comprobanteId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SecuenciaFiscal s where s.id=:id and s.tenantId=:tenantId and s.empresaId=:empresaId")
    Optional<SecuenciaFiscal> bloquear(@Param("id")UUID id,@Param("tenantId")UUID tenantId,@Param("empresaId")UUID empresaId);

    @Query("""
        select (count(s)>0) from SecuenciaFiscal s
         where s.tenantId=:tenantId and s.empresaId=:empresaId and s.comprobanteId=:comprobanteId
           and (:excluirId is null or s.id<>:excluirId)
           and (:numeroFinal is null or s.numeroInicial<=:numeroFinal)
           and (s.numeroFinal is null or s.numeroFinal>=:numeroInicial)
        """)
    boolean existeSolapamiento(@Param("tenantId")UUID tenantId,@Param("empresaId")UUID empresaId,
                               @Param("comprobanteId")UUID comprobanteId,@Param("numeroInicial")long numeroInicial,
                               @Param("numeroFinal")Long numeroFinal,@Param("excluirId")UUID excluirId);

    @Query("""
        select s from SecuenciaFiscal s, TipoComprobanteFiscal c
         where c.id=s.comprobanteId and c.tenantId=s.tenantId and c.empresaId=s.empresaId
           and s.tenantId=:tenantId and s.empresaId=:empresaId
           and (:activo is null or s.activo=:activo)
           and (:buscar='' or lower(s.codigo) like lower(concat('%',:buscar,'%'))
             or lower(c.codigo) like lower(concat('%',:buscar,'%'))
             or lower(c.nombre) like lower(concat('%',:buscar,'%'))
             or lower(c.prefijo) like lower(concat('%',:buscar,'%')))
         order by c.codigo,s.numeroInicial
        """)
    Page<SecuenciaFiscal> buscar(@Param("tenantId")UUID tenantId,@Param("empresaId")UUID empresaId,
                                 @Param("buscar")String buscar,@Param("activo")Boolean activo,Pageable pageable);
}
