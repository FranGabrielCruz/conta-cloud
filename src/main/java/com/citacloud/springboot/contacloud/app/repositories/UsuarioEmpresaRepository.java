package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.UsuarioEmpresa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface UsuarioEmpresaRepository extends JpaRepository<UsuarioEmpresa,UUID> {
    @Query("""
      select distinct ue from UsuarioEmpresa ue
      join fetch ue.usuario u
      join fetch ue.rol r
      left join fetch r.permisos
      where ue.empresaId=:empresaId and lower(u.usuario)=lower(:usuario)
        and ue.activo=true and u.activo=true
      """)
    Optional<UsuarioEmpresa> findForAuthentication(@Param("empresaId") UUID empresaId,
                                                    @Param("usuario") String usuario);
    Optional<UsuarioEmpresa> findByIdAndEmpresaId(UUID id,UUID empresaId);
    Optional<UsuarioEmpresa> findByUsuarioIdAndEmpresaId(UUID usuarioId,UUID empresaId);
    boolean existsByUsuarioIdAndEmpresaId(UUID usuarioId,UUID empresaId);
    long countByEmpresaIdAndRolIdAndActivoTrue(UUID empresaId,UUID rolId);
    long countByUsuarioIdAndActivoTrue(UUID usuarioId);
    @Query("""
      select ue from UsuarioEmpresa ue where ue.empresaId=:empresaId
        and (:buscar='' or lower(ue.usuario.usuario) like lower(concat('%',:buscar,'%'))
          or lower(ue.usuario.nombre) like lower(concat('%',:buscar,'%'))
          or lower(ue.usuario.apellido) like lower(concat('%',:buscar,'%'))
          or lower(concat(ue.usuario.nombre,' ',ue.usuario.apellido)) like lower(concat('%',:buscar,'%'))
          or lower(coalesce(ue.usuario.correo,'')) like lower(concat('%',:buscar,'%'))
          or lower(coalesce(ue.usuario.telefono,'')) like lower(concat('%',:buscar,'%')))
      """)
    Page<UsuarioEmpresa> buscar(@Param("empresaId") UUID empresaId,@Param("buscar") String buscar,Pageable pageable);
    @Query("""
      select ue from UsuarioEmpresa ue where ue.empresaId=:empresaId and ue.activo=:activo
        and (:buscar='' or lower(ue.usuario.usuario) like lower(concat('%',:buscar,'%'))
          or lower(ue.usuario.nombre) like lower(concat('%',:buscar,'%'))
          or lower(ue.usuario.apellido) like lower(concat('%',:buscar,'%'))
          or lower(concat(ue.usuario.nombre,' ',ue.usuario.apellido)) like lower(concat('%',:buscar,'%'))
          or lower(coalesce(ue.usuario.correo,'')) like lower(concat('%',:buscar,'%'))
          or lower(coalesce(ue.usuario.telefono,'')) like lower(concat('%',:buscar,'%')))
      """)
    Page<UsuarioEmpresa> buscarPorEstado(@Param("empresaId") UUID empresaId,@Param("buscar") String buscar,
                                         @Param("activo") boolean activo,Pageable pageable);
    @Query("select ue.rol.id as rolId,count(ue.id) as cantidad from UsuarioEmpresa ue where ue.empresaId=:empresaId and ue.rol.id in :roles group by ue.rol.id")
    List<RolCantidad> contarPorRoles(@Param("empresaId") UUID empresaId,@Param("roles") Collection<UUID> roles);
    interface RolCantidad { UUID getRolId(); long getCantidad(); }
}
