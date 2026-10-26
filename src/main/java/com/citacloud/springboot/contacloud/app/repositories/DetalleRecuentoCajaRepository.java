package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.DetalleRecuentoCaja; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface DetalleRecuentoCajaRepository extends JpaRepository<DetalleRecuentoCaja,UUID>{List<DetalleRecuentoCaja> findAllByRecuentoIdOrderByOrdenAsc(UUID id);}
