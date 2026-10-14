package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ExistenciaInventarioReporteService {
    private static final DateTimeFormatter DATE_TIME=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final ExistenciaInventarioConsultaService queries;private final ExistenciaInventarioPdfService pdf;
    private final EmpresaRepository companies;private final DatosEmpresaRepository companyData;private final UsuarioRepository users;
    private final SucursalRepository branches;private final AlmacenRepository warehouses;private final ProductoCategoriaRepository categories;
    private final LocalFileStorageService storage;
    public ExistenciaInventarioReporteService(ExistenciaInventarioConsultaService queries,ExistenciaInventarioPdfService pdf,
            EmpresaRepository companies,DatosEmpresaRepository companyData,UsuarioRepository users,SucursalRepository branches,
            AlmacenRepository warehouses,ProductoCategoriaRepository categories,LocalFileStorageService storage){this.queries=queries;this.pdf=pdf;
        this.companies=companies;this.companyData=companyData;this.users=users;this.branches=branches;this.warehouses=warehouses;this.categories=categories;this.storage=storage;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    @PreAuthorize("@empresaModuloService.habilitado('INVENTARIO') and hasAuthority('inventario_existencias.ver') and hasAuthority('inventario_existencias.pdf')")
    public ArchivoPdfDto generar(String buscar,UUID sucursal,UUID almacen,UUID categoria,String estado,String stock){
        var rows=queries.reporte(buscar,sucursal,almacen,categoria,estado,stock);if(rows.isEmpty())throw new ReglaNegocioException("No hay existencias para generar el reporte.");
        var principal=TenantContext.principalActual();Empresa company=companies.findByIdAndTenantId(principal.empresaId(),principal.tenantId()).orElseThrow();
        DatosEmpresa data=companyData.findByEmpresaId(company.getId()).orElse(null);Usuario user=users.findByIdAndTenantId(principal.usuarioId(),principal.tenantId()).orElseThrow();
        ZoneId zone;try{zone=ZoneId.of(company.getZonaHoraria());}catch(Exception ignored){zone=ZoneId.of("America/Santo_Domingo");}
        OffsetDateTime now=OffsetDateTime.now(zone);byte[] logo=company.getLogoObjectKey()==null?null:storage.load(company.getLogoObjectKey(),5L*1024*1024).orElse(null);
        String trade=data==null?company.getNombre():clean(data.getNombreComercial(),company.getNombre());String legal=data==null?company.getNombre():clean(data.getRazonSocial(),company.getNombre());
        var lines=rows.stream().map(x->new LineaReporteExistenciaInventario(x.producto(),x.codigo(),x.unidad(),x.sucursal(),x.almacen(),MovimientoInventarioReporteService.number(x.existencia()))).toList();
        long products=rows.stream().map(ExistenciaInventarioDto::productoId).distinct().count();ReporteExistenciaInventarioData report=new ReporteExistenciaInventarioData(trade,legal,company.getIdentificacionFiscal(),data==null?null:data.getDireccion(),data==null?null:data.getTelefono(),data==null?null:data.getCorreo(),logo,DATE_TIME.format(now),clean(buscar,"Sin búsqueda"),branch(sucursal,company.getId()),warehouse(almacen,principal.tenantId(),company.getId()),category(categoria,principal.tenantId(),company.getId()),estadoLabel(estado),stockLabel(stock),products,sucursal==null,almacen==null,PurchaseOrderService.issuedByDisplayName(user),now,lines);
        return new ArchivoPdfDto("Existencias_Inventario_"+DateTimeFormatter.ofPattern("dd-MM-yyyy_HH-mm").format(now)+".pdf",pdf.generar(report));
    }
    private String branch(UUID id,UUID empresa){return id==null?"Todas":branches.findByIdAndEmpresaId(id,empresa).map(Sucursal::getNombre).orElse("—");}
    private String warehouse(UUID id,UUID tenant,UUID empresa){return id==null?"Todos":warehouses.findByIdAndTenantIdAndEmpresaId(id,tenant,empresa).map(Almacen::getNombre).orElse("—");}
    private String category(UUID id,UUID tenant,UUID empresa){return id==null?"Todas":categories.findByIdAndTenantIdAndEmpresaId(id,tenant,empresa).map(ProductoCategoria::getNombre).orElse("—");}
    private static String estadoLabel(String x){return switch(x==null?"ACTIVOS":x){case"TODOS"->"Todos";case"INACTIVOS"->"Inactivos";default->"Activos";};}
    private static String stockLabel(String x){return switch(x==null?"TODOS":x){case"CON_EXISTENCIA"->"Con existencia";case"SIN_EXISTENCIA"->"Sin existencia";case"NEGATIVA"->"Existencia negativa";default->"Todos";};}
    private static String clean(String x,String fallback){return x==null||x.isBlank()?fallback:x.trim();}
}
