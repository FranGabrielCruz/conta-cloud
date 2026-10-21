package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.MotivoDescuentoVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface MotivoDescuentoVentaRepository extends JpaRepository<MotivoDescuentoVenta,UUID>{List<MotivoDescuentoVenta> findAllByTenantIdAndEmpresaIdOrderByNombre(UUID tenant,UUID empresa);List<MotivoDescuentoVenta> findAllByTenantIdAndEmpresaIdAndActivoTrueOrderByNombre(UUID tenant,UUID empresa);Optional<MotivoDescuentoVenta> findByIdAndTenantIdAndEmpresaId(UUID id,UUID tenant,UUID empresa);boolean existsByTenantIdAndEmpresaIdAndNombreNormalizado(UUID tenant,UUID empresa,String nombre);boolean existsByTenantIdAndEmpresaIdAndNombreNormalizadoAndIdNot(UUID tenant,UUID empresa,String nombre,UUID id);}
