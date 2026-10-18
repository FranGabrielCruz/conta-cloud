package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.LineaFacturaVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface LineaFacturaVentaRepository extends JpaRepository<LineaFacturaVenta,UUID>{
    List<LineaFacturaVenta> findAllByFacturaIdOrderByOrden(UUID facturaId);
}
