package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.PagoVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface PagoVentaRepository extends JpaRepository<PagoVenta,UUID>{}
