package com.citacloud.springboot.contacloud.app.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Component
public class BootstrapConfig implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapConfig.class);
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    @Value("${contacloud.bootstrap.empresa-codigo:}") private String empresaCodigo;
    @Value("${contacloud.bootstrap.empresa-nombre:}") private String empresaNombre;
    @Value("${contacloud.bootstrap.usuario:}") private String usuario;
    @Value("${contacloud.bootstrap.password:}") private String password;
    public BootstrapConfig(JdbcTemplate jdbc, PasswordEncoder encoder) { this.jdbc = jdbc; this.encoder = encoder; }

    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (empresaCodigo.isBlank() || empresaNombre.isBlank() || usuario.isBlank() || password.isBlank()) return;
        Integer existe = jdbc.queryForObject("SELECT COUNT(*) FROM empresas WHERE UPPER(codigo)=UPPER(?)", Integer.class, empresaCodigo);
        if (existe != null && existe > 0) return;
        UUID empresaId = UUID.randomUUID(), rolId = UUID.randomUUID(), usuarioId = UUID.randomUUID();
        jdbc.update("INSERT INTO empresas(id,codigo,nombre) VALUES (?,?,?)", empresaId, empresaCodigo.trim().toUpperCase(), empresaNombre.trim());
        jdbc.update("INSERT INTO datos_empresa(empresa_id,nombre_comercial,razon_social) VALUES (?,?,?)",
            empresaId, empresaNombre.trim(), empresaNombre.trim());
        jdbc.update("INSERT INTO sucursales(empresa_id,codigo,nombre,principal,activo) VALUES (?,?,?,?,TRUE)",
            empresaId, "PRINCIPAL", "Sucursal Principal", true);
        jdbc.update("INSERT INTO monedas(empresa_id,codigo_iso,nombre,simbolo,decimales,moneda_base,activo) " +
            "VALUES (?,?,?,?,2,TRUE,TRUE)", empresaId, "DOP", "Peso dominicano", "RD$");
        jdbc.update("INSERT INTO roles(id,empresa_id,codigo,nombre) VALUES (?,?,?,?)", rolId, empresaId, "ADMINISTRADOR", "Administrador");
        jdbc.update("INSERT INTO rol_permisos(empresa_id,rol_id,permiso_id) SELECT ?,?,id FROM permisos", empresaId, rolId);
        jdbc.update("INSERT INTO usuarios(id,empresa_id,usuario,nombre,password_hash) VALUES (?,?,?,?,?)",
            usuarioId, empresaId, usuario.trim(), usuario.trim(), encoder.encode(password));
        jdbc.update("INSERT INTO usuario_roles(empresa_id,usuario_id,rol_id) VALUES (?,?,?)", empresaId, usuarioId, rolId);
        log.info("Tenant inicial creado: {}", empresaCodigo.trim().toUpperCase());
    }
}
