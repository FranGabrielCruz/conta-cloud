package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.PeriodoFiscal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
public interface PeriodoFiscalRepository extends JpaRepository<PeriodoFiscal, UUID> {
    Optional<PeriodoFiscal> findFirstByEmpresaIdAndFechaInicialLessThanEqualAndFechaFinalGreaterThanEqual(UUID empresaId, LocalDate inicio, LocalDate fin);
}
