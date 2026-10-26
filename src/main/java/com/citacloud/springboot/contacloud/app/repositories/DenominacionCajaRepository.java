package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.DenominacionCaja; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface DenominacionCajaRepository extends JpaRepository<DenominacionCaja,UUID>{List<DenominacionCaja> findAllByTenantIdAndEmpresaIdAndMonedaIdAndActiveTrueOrderByOrdenAsc(UUID t,UUID e,UUID m);}
