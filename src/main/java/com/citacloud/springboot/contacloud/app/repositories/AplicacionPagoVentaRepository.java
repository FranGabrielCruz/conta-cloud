package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.AplicacionPagoVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface AplicacionPagoVentaRepository extends JpaRepository<AplicacionPagoVenta,UUID>{}
