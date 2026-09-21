package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.Rol;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface RolRepository extends JpaRepository<Rol,UUID> {
    @EntityGraph(attributePaths="permisos") Optional<Rol> findByIdAndEmpresaId(UUID id,UUID empresaId);
    List<Rol> findAllByEmpresaIdAndActivoTrueOrderByNombre(UUID empresaId);
    boolean existsByEmpresaIdAndNombreIgnoreCase(UUID empresaId,String nombre);
    boolean existsByEmpresaIdAndNombreIgnoreCaseAndIdNot(UUID empresaId,String nombre,UUID id);
    @Query("select r from Rol r where r.empresaId=:empresaId and (:buscar='' or lower(r.nombre) like lower(concat('%',:buscar,'%')) or lower(coalesce(r.descripcion,'')) like lower(concat('%',:buscar,'%')))")
    Page<Rol> buscar(@Param("empresaId") UUID empresaId,@Param("buscar") String buscar,Pageable pageable);
    @Query("select r from Rol r where r.empresaId=:empresaId and r.activo=:activo and (:buscar='' or lower(r.nombre) like lower(concat('%',:buscar,'%')) or lower(coalesce(r.descripcion,'')) like lower(concat('%',:buscar,'%')))")
    Page<Rol> buscarPorEstado(@Param("empresaId") UUID empresaId,@Param("buscar") String buscar,@Param("activo") boolean activo,Pageable pageable);
}
