package com.citacloud.springboot.contacloud.app.services;

import com.citacloud.springboot.contacloud.app.dto.*;
import com.citacloud.springboot.contacloud.app.mappers.UsuarioMapper;
import com.citacloud.springboot.contacloud.app.models.*;
import com.citacloud.springboot.contacloud.app.repositories.*;
import com.citacloud.springboot.contacloud.app.security.TenantContext;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class UsuarioService {
    private static final Set<Integer> TAMANOS = Set.of(10, 25, 50, 100);
    private static final Pattern CORREO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final UsuarioRepository usuarios;
    private final UsuarioEmpresaRepository accesos;
    private final UsuarioSucursalRepository usuarioSucursales;
    private final RolRepository roles;
    private final SucursalRepository sucursales;
    private final EmpresaRepository empresas;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioMapper mapper;
    private final AuditoriaService auditoria;

    public UsuarioService(UsuarioRepository usuarios, UsuarioEmpresaRepository accesos,
                          UsuarioSucursalRepository usuarioSucursales, RolRepository roles,
                          SucursalRepository sucursales, EmpresaRepository empresas,
                          PasswordEncoder passwordEncoder, UsuarioMapper mapper, AuditoriaService auditoria) {
        this.usuarios = usuarios; this.accesos = accesos; this.usuarioSucursales = usuarioSucursales;
        this.roles = roles; this.sucursales = sucursales; this.empresas = empresas;
        this.passwordEncoder = passwordEncoder; this.mapper = mapper; this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyAuthority('USUARIO_VER','usuarios.ver')")
    public Page<UsuarioDto> buscar(String texto, Boolean activo, int pagina, int tamano) {
        validarPagina(pagina, tamano);
        UUID empresaId = TenantContext.requerirEmpresaId();
        Pageable pageable = PageRequest.of(pagina, tamano, Sort.by("usuario.usuario").ascending());
        Page<UsuarioEmpresa> page = activo == null
            ? accesos.buscar(empresaId, limpiar(texto), pageable)
            : accesos.buscarPorEstado(empresaId, limpiar(texto), activo, pageable);
        Map<UUID, Set<UUID>> asignadas = usuarioSucursales.findAllByUsuarioEmpresaIdIn(
                page.getContent().stream().map(UsuarioEmpresa::getId).toList()).stream()
            .collect(Collectors.groupingBy(UsuarioSucursal::getUsuarioEmpresaId,
                Collectors.mapping(UsuarioSucursal::getSucursalId, Collectors.toSet())));
        Map<UUID, String> nombres = sucursales.findAllByEmpresaIdAndActivoTrueOrderByNombre(empresaId).stream()
            .collect(Collectors.toMap(Sucursal::getId, Sucursal::getNombre));
        return page.map(a -> {
            Set<UUID> ids = asignadas.getOrDefault(a.getId(), Set.of());
            List<String> lista = ids.stream().map(nombres::get).filter(Objects::nonNull).sorted().toList();
            return mapper.toDto(a, ids, lista);
        });
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyAuthority('USUARIO_VER','usuarios.ver')")
    public UsuarioDto obtener(UUID accesoId) {
        UsuarioEmpresa acceso = accesoSeguro(accesoId);
        Set<UUID> ids = usuarioSucursales.findAllByUsuarioEmpresaId(accesoId).stream()
            .map(UsuarioSucursal::getSucursalId).collect(Collectors.toSet());
        Map<UUID,String> nombres = sucursales.findAllByIdInAndEmpresaId(ids, TenantContext.requerirEmpresaId()).stream()
            .collect(Collectors.toMap(Sucursal::getId, Sucursal::getNombre));
        return mapper.toDto(acceso, ids, ids.stream().map(nombres::get).filter(Objects::nonNull).sorted().toList());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyAuthority('USUARIO_VER','usuarios.ver','USUARIO_CREAR','usuarios.crear','USUARIO_EDITAR','usuarios.editar')")
    public List<OpcionSeguridadDto> rolesDisponibles() {
        return roles.findAllByEmpresaIdAndActivoTrueOrderByNombre(TenantContext.requerirEmpresaId()).stream()
            .map(r -> new OpcionSeguridadDto(r.getId(), r.getNombre(), r.isActivo())).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyAuthority('USUARIO_VER','usuarios.ver','USUARIO_CREAR','usuarios.crear','USUARIO_EDITAR','usuarios.editar')")
    public List<OpcionSeguridadDto> sucursalesDisponibles() {
        return sucursales.findAllByEmpresaIdAndActivoTrueOrderByNombre(TenantContext.requerirEmpresaId()).stream()
            .map(s -> new OpcionSeguridadDto(s.getId(), s.getNombre(), s.isActivo())).toList();
    }

    @Transactional
    @PreAuthorize("hasAnyAuthority('USUARIO_CREAR','usuarios.crear')")
    public UsuarioDto crear(UsuarioInputDto input, String clave, String confirmarClave) {
        UUID empresaId = TenantContext.requerirEmpresaId();
        UUID tenantId = TenantContext.requerirTenantId();
        DatosValidados datos = validar(input, clave, confirmarClave, true, null);
        Optional<Usuario> identidadExistente = usuarios.findByTenantIdAndUsuarioIgnoreCase(tenantId, datos.usuario());
        Usuario usuario;
        if (identidadExistente.isPresent()) {
            usuario = identidadExistente.get();
            if (accesos.existsByUsuarioIdAndEmpresaId(usuario.getId(), empresaId))
                throw new ReglaNegocioException("El usuario ya tiene acceso a esta empresa.");
            if (!passwordEncoder.matches(clave, usuario.getPasswordHash()))
                throw new ReglaNegocioException("El usuario ya existe en el tenant. Use su contraseña actual para asociarlo a esta empresa.");
            usuario.setActivo(true);
        } else {
            usuario = new Usuario(tenantId, empresaId, datos.usuario(), datos.nombre(), datos.apellido(),
                passwordEncoder.encode(clave));
        }
        usuario.setNombre(datos.nombre()); usuario.setApellido(datos.apellido());
        usuario.setCorreo(datos.correo()); usuario.setTelefono(datos.telefono()); usuario.setActivo(true);
        usuarios.save(usuario);
        UsuarioEmpresa acceso = new UsuarioEmpresa(usuario, empresaId, datos.rol());
        acceso.setActivo(input.activo()); acceso.setAccesoTodasSucursales(input.todasSucursales());
        accesos.saveAndFlush(acceso);
        guardarSucursales(acceso.getId(), datos.sucursales(), input.todasSucursales());
        usuario.setActivo(accesos.countByUsuarioIdAndActivoTrue(usuario.getId()) > 0);
        usuarios.save(usuario);
        auditoria.registrar("USUARIO_CREADO", "Usuario", usuario.getId(), "{}");
        return obtenerSinAutorizacion(acceso);
    }

    @Transactional
    @PreAuthorize("hasAnyAuthority('USUARIO_EDITAR','usuarios.editar')")
    public UsuarioDto actualizar(UUID accesoId, UsuarioInputDto input, String clave, String confirmarClave) {
        UsuarioEmpresa acceso = accesoSeguro(accesoId);
        Usuario usuario = acceso.getUsuario();
        DatosValidados datos = validar(input, clave, confirmarClave, false, usuario.getId());
        boolean desactivaAdmin = esAdministrador(acceso.getRol()) && (!input.activo() || !esAdministrador(datos.rol()));
        if (desactivaAdmin) validarNoEsUltimoAdministrador(acceso);
        usuario.setUsuario(datos.usuario()); usuario.setNombre(datos.nombre()); usuario.setApellido(datos.apellido());
        usuario.setCorreo(datos.correo()); usuario.setTelefono(datos.telefono());
        if (!limpiar(clave).isEmpty()) {
            usuario.setPasswordHash(passwordEncoder.encode(clave));
            auditoria.registrar("CONTRASENA_USUARIO_CAMBIADA", "Usuario", usuario.getId(), "{}");
        }
        acceso.setRol(datos.rol()); acceso.setActivo(input.activo());
        acceso.setAccesoTodasSucursales(input.todasSucursales());
        accesos.save(acceso);
        guardarSucursales(accesoId, datos.sucursales(), input.todasSucursales());
        usuario.setActivo(accesos.countByUsuarioIdAndActivoTrue(usuario.getId()) > 0);
        usuarios.save(usuario);
        auditoria.registrar("USUARIO_EDITADO", "Usuario", usuario.getId(), "{}");
        return obtenerSinAutorizacion(acceso);
    }

    @Transactional
    @PreAuthorize("hasAnyAuthority('USUARIO_DESACTIVAR','usuarios.desactivar')")
    public void desactivar(UUID accesoId) {
        UsuarioEmpresa acceso = accesoSeguro(accesoId);
        if (!acceso.isActivo()) return;
        if (esAdministrador(acceso.getRol())) validarNoEsUltimoAdministrador(acceso);
        acceso.setActivo(false); accesos.saveAndFlush(acceso);
        Usuario usuario = acceso.getUsuario();
        if (accesos.countByUsuarioIdAndActivoTrue(usuario.getId()) == 0) {
            usuario.setActivo(false); usuarios.save(usuario);
        }
        auditoria.registrar("USUARIO_DESACTIVADO", "Usuario", usuario.getId(), "{}");
    }

    private DatosValidados validar(UsuarioInputDto input, String clave, String confirmar, boolean nuevo, UUID usuarioId) {
        if (input == null) throw new ReglaNegocioException("Los datos del usuario son obligatorios.");
        String usuario = requerido(input.usuario(), "Usuario", 60).toLowerCase(Locale.ROOT);
        String nombre = requerido(input.nombre(), "Nombre", 150);
        String apellido = requerido(input.apellido(), "Apellido", 100);
        String correo = limpiar(input.correo()).toLowerCase(Locale.ROOT);
        if (!correo.isEmpty() && !CORREO.matcher(correo).matches()) throw new ReglaNegocioException("El correo no tiene un formato válido.");
        String telefono = CompanyConfigurationService.normalizePhone(input.telefono());
        if (usuarioId != null && usuarios.existsByTenantIdAndUsuarioIgnoreCaseAndIdNot(TenantContext.requerirTenantId(), usuario, usuarioId))
            throw new ReglaNegocioException("Ya existe un usuario con ese nombre en el tenant.");
        validarClave(clave, confirmar, nuevo);
        Rol rol = roles.findByIdAndEmpresaId(input.rolId(), TenantContext.requerirEmpresaId())
            .filter(Rol::isActivo).orElseThrow(() -> new ReglaNegocioException("Seleccione un rol activo de la empresa."));
        Set<UUID> ids = input.sucursalIds() == null ? Set.of() : Set.copyOf(input.sucursalIds());
        List<Sucursal> seleccionadas = input.todasSucursales() ? List.of()
            : sucursales.findAllByIdInAndEmpresaId(ids, TenantContext.requerirEmpresaId());
        if (!input.todasSucursales() && (ids.isEmpty() || seleccionadas.size() != ids.size() || seleccionadas.stream().anyMatch(s -> !s.isActivo())))
            throw new ReglaNegocioException("Seleccione al menos una sucursal activa de la empresa.");
        return new DatosValidados(usuario, nombre, apellido, correo.isEmpty() ? null : correo,
            telefono, rol, seleccionadas.stream().map(Sucursal::getId).collect(Collectors.toSet()));
    }

    private void validarClave(String clave, String confirmar, boolean requerida) {
        String a = clave == null ? "" : clave;
        String b = confirmar == null ? "" : confirmar;
        if (!requerida && a.isBlank() && b.isBlank()) return;
        if (a.length() < 8 || a.length() > 72) throw new ReglaNegocioException("La contraseña debe tener entre 8 y 72 caracteres.");
        if (!a.equals(b)) throw new ReglaNegocioException("Las contraseñas no coinciden.");
    }

    private void guardarSucursales(UUID accesoId, Set<UUID> ids, boolean todas) {
        usuarioSucursales.deleteAllByUsuarioEmpresaId(accesoId);
        if (!todas) usuarioSucursales.saveAll(ids.stream().map(id -> new UsuarioSucursal(accesoId, id)).toList());
    }

    private void validarNoEsUltimoAdministrador(UsuarioEmpresa acceso) {
        empresas.findWithLockById(acceso.getEmpresaId()).orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
        if (accesos.countByEmpresaIdAndRolIdAndActivoTrue(acceso.getEmpresaId(), acceso.getRol().getId()) <= 1)
            throw new ReglaNegocioException("No es posible desactivar o cambiar el rol del último administrador activo.");
    }

    private UsuarioEmpresa accesoSeguro(UUID id) {
        return accesos.findByIdAndEmpresaId(id, TenantContext.requerirEmpresaId())
            .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
    }

    private UsuarioDto obtenerSinAutorizacion(UsuarioEmpresa acceso) {
        Set<UUID> ids = usuarioSucursales.findAllByUsuarioEmpresaId(acceso.getId()).stream()
            .map(UsuarioSucursal::getSucursalId).collect(Collectors.toSet());
        Map<UUID,String> nombres = sucursales.findAllByIdInAndEmpresaId(ids, acceso.getEmpresaId()).stream()
            .collect(Collectors.toMap(Sucursal::getId, Sucursal::getNombre));
        return mapper.toDto(acceso, ids, ids.stream().map(nombres::get).filter(Objects::nonNull).sorted().toList());
    }

    private static boolean esAdministrador(Rol rol) { return rol.isProtegido() || "ADMINISTRADOR".equalsIgnoreCase(rol.getNombre()); }
    private static String requerido(String valor, String campo, int max) {
        String limpio = limpiar(valor);
        if (limpio.isEmpty()) throw new ReglaNegocioException(campo + " es obligatorio.");
        if (limpio.length() > max) throw new ReglaNegocioException(campo + " excede " + max + " caracteres.");
        return limpio;
    }
    private static String limpiar(String valor) { return valor == null ? "" : valor.trim(); }
    private static void validarPagina(int pagina, int tamano) {
        if (pagina < 0 || !TAMANOS.contains(tamano)) throw new IllegalArgumentException("Paginación inválida.");
    }
    private record DatosValidados(String usuario, String nombre, String apellido, String correo, String telefono,
                                  Rol rol, Set<UUID> sucursales) {}
}
