package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.Sucursal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface SucursalRepository extends JpaRepository<Sucursal, UUID> {
    Page<Sucursal> findAllByEmpresaId(UUID empresaId, Pageable pageable);
    long countByEmpresaIdAndActivoTrue(UUID empresaId);
}
