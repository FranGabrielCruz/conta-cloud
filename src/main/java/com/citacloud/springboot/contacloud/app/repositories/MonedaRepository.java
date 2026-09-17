package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.Moneda;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface MonedaRepository extends JpaRepository<Moneda, UUID> {
    Optional<Moneda> findByEmpresaIdAndMonedaBaseTrue(UUID empresaId);
}
