package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.CreditoCliente;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface CreditoClienteRepository extends JpaRepository<CreditoCliente,UUID>{}
