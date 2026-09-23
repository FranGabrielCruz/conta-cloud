package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
    Optional<Usuario> findByEmpresaIdAndUsuarioIgnoreCaseAndActivoTrue(UUID empresaId, String usuario);
    Optional<Usuario> findByTenantIdAndUsuarioIgnoreCaseAndActivoTrue(UUID tenantId, String usuario);
    Optional<Usuario> findByTenantIdAndUsuarioIgnoreCase(UUID tenantId, String usuario);
    Optional<Usuario> findByIdAndTenantId(UUID id, UUID tenantId);
    boolean existsByTenantIdAndUsuarioIgnoreCase(UUID tenantId, String usuario);
    boolean existsByTenantIdAndUsuarioIgnoreCaseAndIdNot(UUID tenantId, String usuario, UUID id);
    long countByEmpresaIdAndActivoTrue(UUID empresaId);
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Usuario u set u.passwordHash=:passwordHash where u.id=:usuarioId and u.tenantId=:tenantId")
    int actualizarPasswordHash(@Param("usuarioId") UUID usuarioId,@Param("tenantId") UUID tenantId,
                               @Param("passwordHash") String passwordHash);
}
