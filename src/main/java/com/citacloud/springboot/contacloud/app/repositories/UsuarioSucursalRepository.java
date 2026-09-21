package com.citacloud.springboot.contacloud.app.repositories;
import com.citacloud.springboot.contacloud.app.models.UsuarioSucursal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface UsuarioSucursalRepository extends JpaRepository<UsuarioSucursal,UsuarioSucursal.Clave> {
    List<UsuarioSucursal> findAllByUsuarioEmpresaId(UUID usuarioEmpresaId);
    List<UsuarioSucursal> findAllByUsuarioEmpresaIdIn(Collection<UUID> ids);
    void deleteAllByUsuarioEmpresaId(UUID usuarioEmpresaId);
}
