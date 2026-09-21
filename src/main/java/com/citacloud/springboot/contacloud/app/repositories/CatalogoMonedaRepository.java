package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.CatalogoMoneda;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CatalogoMonedaRepository extends JpaRepository<CatalogoMoneda, String> {
    List<CatalogoMoneda> findAllByActivoTrueOrderByCodigoIso();
}
