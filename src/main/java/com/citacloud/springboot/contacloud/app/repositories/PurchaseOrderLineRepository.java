package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.LineaOrdenCompra;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface PurchaseOrderLineRepository extends JpaRepository<LineaOrdenCompra,UUID> {}
