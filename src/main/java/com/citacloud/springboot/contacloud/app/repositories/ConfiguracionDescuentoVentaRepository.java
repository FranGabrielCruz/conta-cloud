package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.ConfiguracionDescuentoVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ConfiguracionDescuentoVentaRepository extends JpaRepository<ConfiguracionDescuentoVenta,UUID>{Optional<ConfiguracionDescuentoVenta> findByTenantIdAndEmpresaId(UUID tenant,UUID empresa);}
