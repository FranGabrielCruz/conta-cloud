package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.LineaTransferenciaInventario;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface LineaTransferenciaInventarioRepository extends JpaRepository<LineaTransferenciaInventario,UUID>{
 List<LineaTransferenciaInventario> findAllByTransferenciaIdOrderByOrden(UUID transferenciaId);
 void deleteAllByTransferenciaId(UUID transferenciaId);
}
