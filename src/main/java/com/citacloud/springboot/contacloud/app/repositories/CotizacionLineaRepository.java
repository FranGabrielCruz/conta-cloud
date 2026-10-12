package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.LineaCotizacion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface CotizacionLineaRepository extends JpaRepository<LineaCotizacion,UUID> {}
