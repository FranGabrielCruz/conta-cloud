package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.RolPermiso;
import com.citacloud.springboot.contacloud.app.models.RolPermisoId;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RolPermisoRepository extends JpaRepository<RolPermiso, RolPermisoId> {
    @Modifying(flushAutomatically = true)
    @Query("delete from RolPermiso rp where rp.id.rolId = :rolId")
    void deleteAllByRolId(@Param("rolId") UUID rolId);
}
