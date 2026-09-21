package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.Permiso;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PermisoRepository extends JpaRepository<Permiso,UUID> {
    List<Permiso> findAllByOrderByModuloAscRecursoAscCodigoAsc();
    List<Permiso> findAllByIdIn(Collection<UUID> ids);
}
