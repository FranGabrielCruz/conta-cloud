package com.citacloud.springboot.contacloud.app.repositories;

import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
class UsuarioEmpresaAuthenticationRepositoryTest {

    @Autowired UsuarioEmpresaRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @Test
    void cargaPermisosAntesDeCerrarLaSesionJpa() {
        UUID tenantId=UUID.randomUUID(),empresaId=UUID.randomUUID(),usuarioId=UUID.randomUUID();
        UUID rolId=UUID.randomUUID(),permisoId=UUID.randomUUID(),accesoId=UUID.randomUUID();
        jdbc.update("insert into empresas(id,tenant_id,codigo,nombre,zona_horaria,activo,creado_en,actualizado_en) values (?,?,?,?,?,true,current_timestamp,current_timestamp)",empresaId,tenantId,"EMPRESA01","Empresa","America/Santo_Domingo");
        jdbc.update("insert into usuarios(id,tenant_id,empresa_id,usuario,nombre,apellido,password_hash,activo) values (?,?,?,?,?,?,?,true)",usuarioId,tenantId,empresaId,"admin","Administrador","","hash");
        jdbc.update("insert into roles(id,empresa_id,codigo,nombre,protegido,activo) values (?,?,?,?,true,true)",rolId,empresaId,"ADMINISTRADOR","Administrador");
        jdbc.update("insert into permisos(id,codigo,nombre,modulo) values (?,?,?,?)",permisoId,"EMPRESA_VER","Ver empresa","CORE");
        jdbc.update("insert into rol_permisos(rol_id,permiso_id) values (?,?)",rolId,permisoId);
        jdbc.update("insert into usuario_empresa(id,usuario_id,empresa_id,rol_id,activo,acceso_todas_sucursales,creado_en,actualizado_en) values (?,?,?,?,true,true,current_timestamp,current_timestamp)",accesoId,usuarioId,empresaId,rolId);
        entityManager.clear();

        var acceso=repository.findForAuthentication(empresaId,"ADMIN").orElseThrow();
        assertThat(Hibernate.isInitialized(acceso.getRol().getPermisos())).isTrue();
        entityManager.clear();

        assertThat(acceso.getRol().getPermisos()).extracting("codigo").containsExactly("EMPRESA_VER");
    }
}
