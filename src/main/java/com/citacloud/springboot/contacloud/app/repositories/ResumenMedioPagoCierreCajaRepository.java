package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.ResumenMedioPagoCierreCaja; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface ResumenMedioPagoCierreCajaRepository extends JpaRepository<ResumenMedioPagoCierreCaja,UUID>{List<ResumenMedioPagoCierreCaja> findAllByCierreIdOrderByOrdenAsc(UUID cierreId);}
