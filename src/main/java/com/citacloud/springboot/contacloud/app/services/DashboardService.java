package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.repositories.EmpresaRepository;
import com.citacloud.springboot.contacloud.app.repositories.MonedaRepository;
import com.citacloud.springboot.contacloud.app.repositories.PeriodoFiscalRepository;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class DashboardService {

    private final EmpresaRepository empresas;
    private final MonedaRepository monedas;
    private final PeriodoFiscalRepository periodos;
    private final JdbcTemplate jdbc;

    public DashboardService(EmpresaRepository empresas, MonedaRepository monedas,
                            PeriodoFiscalRepository periodos, JdbcTemplate jdbc) {
        this.empresas = empresas;
        this.monedas = monedas;
        this.periodos = periodos;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Resumen obtener() {
        var principal = TenantContext.principalActual();
        UUID empresaId = principal.empresaId();
        var empresa = empresas.findById(empresaId)
            .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada"));

        Metricas metricas = new Metricas(
            contar("usuarios", empresaId),
            contar("roles", empresaId),
            contar("sucursales", empresaId),
            contar("monedas", empresaId),
            contar("impuestos", empresaId));

        List<String> pendientes = configuracionPendiente(empresaId);
        List<AccesoRapido> accesos = accesosRapidos(principal.permisos());
        List<Actividad> actividad = jdbc.query("""
                SELECT accion, entidad, fecha_hora
                  FROM auditoria
                 WHERE empresa_id = ?
                 ORDER BY fecha_hora DESC
                 LIMIT 5
                """, (rs, row) -> new Actividad(
                    rs.getString("accion"), rs.getString("entidad"),
                    rs.getObject("fecha_hora", Timestamp.class).toInstant()), empresaId);

        return new Resumen(empresa.getNombre(), metricas, pendientes, accesos, actividad);
    }

    private List<String> configuracionPendiente(UUID empresaId) {
        List<String> pendientes = new ArrayList<>();
        if (contar("datos_empresa", empresaId) == 0) pendientes.add("Completar los datos generales de la empresa");
        if (monedas.findByEmpresaIdAndMonedaBaseTrue(empresaId).isEmpty()) pendientes.add("Definir la moneda base");
        if (contar("configuracion_contable", empresaId) == 0) pendientes.add("Completar la configuración contable");
        LocalDate hoy = LocalDate.now();
        if (periodos.findFirstByEmpresaIdAndFechaInicialLessThanEqualAndFechaFinalGreaterThanEqual(
                empresaId, hoy, hoy).isEmpty()) {
            pendientes.add("Crear el período fiscal actual");
        }
        if (contar("tipos_comprobantes_fiscales", empresaId) == 0) {
            pendientes.add("Configurar los comprobantes fiscales");
        }
        return List.copyOf(pendientes);
    }

    private List<AccesoRapido> accesosRapidos(Set<String> permisos) {
        List<AccesoRapido> accesos = new ArrayList<>();
        agregarAcceso(permisos, accesos, "SUCURSAL_CREAR", "Nueva sucursal", "sucursales", "BUILDING");
        agregarAcceso(permisos, accesos, "USUARIO_CREAR", "Nuevo usuario", "usuarios", "USER_PLUS");
        agregarAcceso(permisos, accesos, "TASA_CAMBIO_CREAR", "Nueva tasa de cambio", "tasas-cambio", "EXCHANGE");
        agregarAcceso(permisos, accesos, "IMPUESTO_CREAR", "Nuevo impuesto", "impuestos", "CALC");
        agregarAcceso(permisos, accesos, "CONFIGURACION_CONTABLE_EDITAR", "Configuración contable",
            "configuracion-contable", "COG");
        return List.copyOf(accesos);
    }

    private void agregarAcceso(Set<String> permisos, List<AccesoRapido> accesos, String permiso,
                               String titulo, String ruta, String icono) {
        if (permisos.contains(permiso)) accesos.add(new AccesoRapido(titulo, ruta, icono));
    }

    private long contar(String tabla, UUID empresaId) {
        // Los nombres se originan exclusivamente en constantes internas, nunca en entrada del usuario.
        Long resultado = jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla + " WHERE empresa_id = ?", Long.class, empresaId);
        return resultado == null ? 0 : resultado;
    }

    public record Resumen(String empresa, Metricas metricas, List<String> pendientes,
                          List<AccesoRapido> accesosRapidos, List<Actividad> actividadReciente) {}
    public record Metricas(long usuarios, long roles, long sucursales, long monedas, long impuestos) {}
    public record AccesoRapido(String titulo, String ruta, String icono) {}
    public record Actividad(String accion, String entidad, Instant fechaHora) {}
}
