package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.Empresa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
public interface EmpresaRepository extends JpaRepository<Empresa, UUID> {
    Optional<Empresa> findByCodigoIgnoreCaseAndActivoTrue(String codigo);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Empresa> findWithLockById(UUID id);
}
