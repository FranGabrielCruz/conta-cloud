package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.MetodoPagoVenta;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface MetodoPagoVentaRepository extends JpaRepository<MetodoPagoVenta,UUID>{
    List<MetodoPagoVenta> findAllByTenantIdAndEmpresaIdAndActiveTrueOrderByDisplayOrderAscNameAsc(UUID tenant,UUID empresa);
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select m from MetodoPagoVenta m where m.id=:id and m.tenantId=:tenant and m.empresaId=:empresa and m.active=true")
    Optional<MetodoPagoVenta> bloquearActivo(@Param("id")UUID id,@Param("tenant")UUID tenant,@Param("empresa")UUID empresa);
}
