package com.citacloud.springboot.contacloud.app.repositories;

import com.citacloud.springboot.contacloud.app.models.MovimientoInventario;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.*;

public interface InventoryMovementRepository extends JpaRepository<MovimientoInventario,UUID> {
    boolean existsByTenantIdAndEmpresaIdAndTipoReferenciaAndReferenciaIdAndProductoIdAndDirection(UUID tenantId,UUID empresaId,String tipo,UUID referenciaId,UUID productoId,String direction);
    List<MovimientoInventario> findAllByTipoReferenciaAndReferenciaIdAndTenantIdAndEmpresaId(String tipo,UUID referenciaId,UUID tenantId,UUID empresaId);
    @Query("""
      select coalesce(sum(case when m.direction='IN' then m.quantity else -m.quantity end),0)
      from MovimientoInventario m where m.tenantId=:tenant and m.empresaId=:empresa
      and m.almacenId=:almacen and m.productoId=:producto
      """)
    BigDecimal saldo(@Param("tenant")UUID tenant,@Param("empresa")UUID empresa,@Param("almacen")UUID almacen,@Param("producto")UUID producto);
}
