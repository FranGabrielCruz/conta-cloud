package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface AutorizacionDescuentoVentaRepository extends JpaRepository<AutorizacionDescuentoVenta,UUID>{
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)@Query("select a from AutorizacionDescuentoVenta a where a.id=:id and a.tenantId=:tenant and a.empresaId=:empresa")
 Optional<AutorizacionDescuentoVenta> bloquear(@Param("id")UUID id,@Param("tenant")UUID tenant,@Param("empresa")UUID empresa);
}
