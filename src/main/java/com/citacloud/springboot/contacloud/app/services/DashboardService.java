package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Service
public class DashboardService {
    private final EmpresaRepository empresas; private final SucursalRepository sucursales;
    private final UsuarioRepository usuarios; private final MonedaRepository monedas;
    private final PeriodoFiscalRepository periodos;
    public DashboardService(EmpresaRepository e, SucursalRepository s, UsuarioRepository u, MonedaRepository m, PeriodoFiscalRepository p) {
        empresas=e; sucursales=s; usuarios=u; monedas=m; periodos=p;
    }
    @Transactional(readOnly = true)
    public Resumen obtener() {
        var empresaId = TenantContext.requerirEmpresaId();
        var empresa = empresas.findById(empresaId).orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada"));
        var hoy = LocalDate.now();
        return new Resumen(empresa.getNombre(), sucursales.countByEmpresaIdAndActivoTrue(empresaId),
            usuarios.countByEmpresaIdAndActivoTrue(empresaId),
            monedas.findByEmpresaIdAndMonedaBaseTrue(empresaId).map(m -> m.getCodigoIso()).orElse("Pendiente"),
            periodos.findFirstByEmpresaIdAndFechaInicialLessThanEqualAndFechaFinalGreaterThanEqual(empresaId, hoy, hoy)
                .map(p -> p.getEstado().name()).orElse("Sin configurar"));
    }
    public record Resumen(String empresa, long sucursalesActivas, long usuariosActivos, String monedaBase, String periodoActual) {}
}
