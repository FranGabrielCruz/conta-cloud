package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.DetalleCierreCaja; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface DetalleCierreCajaRepository extends JpaRepository<DetalleCierreCaja,UUID>{List<DetalleCierreCaja> findAllByCierreIdOrderByOrdenAsc(UUID cierreId);}
