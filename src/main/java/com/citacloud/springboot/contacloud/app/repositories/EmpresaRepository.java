package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.Empresa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface EmpresaRepository extends JpaRepository<Empresa, UUID> {
    Optional<Empresa> findByCodigoIgnoreCaseAndActivoTrue(String codigo);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Empresa> findWithLockById(UUID id);
    Optional<Empresa> findByIdAndTenantId(UUID id,UUID tenantId);
    boolean existsByCodigoIgnoreCase(String codigo);
    @Query("""
      select e from Empresa e
      where (:buscar='' or lower(e.nombre) like lower(concat('%',:buscar,'%')) or lower(e.codigo) like lower(concat('%',:buscar,'%')) or lower(coalesce(e.identificacionFiscal,'')) like lower(concat('%',:buscar,'%')))
      """)
    Page<Empresa> buscarTodas(@Param("buscar")String buscar,Pageable pageable);
    @Query("""
      select e from Empresa e where e.activo=:activo
      and (:buscar='' or lower(e.nombre) like lower(concat('%',:buscar,'%')) or lower(e.codigo) like lower(concat('%',:buscar,'%')) or lower(coalesce(e.identificacionFiscal,'')) like lower(concat('%',:buscar,'%')))
      """)
    Page<Empresa> buscarTodasPorEstado(@Param("buscar")String buscar,@Param("activo")boolean activo,Pageable pageable);
    @Query("""
      select e from Empresa e, UsuarioEmpresa ue where ue.empresaId=e.id and ue.usuario.id=:usuarioId
      and ue.activo=true and e.tenantId=:tenantId
      and (:buscar='' or lower(e.nombre) like lower(concat('%',:buscar,'%')) or lower(e.codigo) like lower(concat('%',:buscar,'%')) or lower(coalesce(e.identificacionFiscal,'')) like lower(concat('%',:buscar,'%')))
      """)
    Page<Empresa> buscarAutorizadas(@Param("tenantId")UUID tenantId,@Param("usuarioId")UUID usuarioId,@Param("buscar")String buscar,Pageable pageable);
    @Query("""
      select e from Empresa e, UsuarioEmpresa ue where ue.empresaId=e.id and ue.usuario.id=:usuarioId
      and ue.activo=true and e.tenantId=:tenantId and e.activo=:activo
      and (:buscar='' or lower(e.nombre) like lower(concat('%',:buscar,'%')) or lower(e.codigo) like lower(concat('%',:buscar,'%')) or lower(coalesce(e.identificacionFiscal,'')) like lower(concat('%',:buscar,'%')))
      """)
    Page<Empresa> buscarAutorizadasPorEstado(@Param("tenantId")UUID tenantId,@Param("usuarioId")UUID usuarioId,@Param("buscar")String buscar,@Param("activo")boolean activo,Pageable pageable);
}
