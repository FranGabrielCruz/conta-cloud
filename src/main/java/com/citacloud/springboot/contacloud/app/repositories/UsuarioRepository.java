package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
    Optional<Usuario> findByEmpresaIdAndUsuarioIgnoreCaseAndActivoTrue(UUID empresaId, String usuario);
    long countByEmpresaIdAndActivoTrue(UUID empresaId);
}
