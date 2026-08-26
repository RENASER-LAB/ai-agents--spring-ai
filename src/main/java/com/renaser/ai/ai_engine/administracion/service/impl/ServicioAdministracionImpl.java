package com.renaser.ai.ai_engine.administracion.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.archivo.entity.*;
import com.renaser.ai.ai_engine.archivo.repository.*;
import com.renaser.ai.ai_engine.archivo.service.*;
import com.renaser.ai.ai_engine.auditoria.entity.*;
import com.renaser.ai.ai_engine.auditoria.repository.*;
import com.renaser.ai.ai_engine.auditoria.service.*;
import com.renaser.ai.ai_engine.notificacion.entity.*;
import com.renaser.ai.ai_engine.notificacion.repository.*;
import com.renaser.ai.ai_engine.notificacion.service.*;
import com.renaser.ai.ai_engine.parametro.entity.*;
import com.renaser.ai.ai_engine.parametro.repository.*;
import com.renaser.ai.ai_engine.parametro.service.*;
import com.renaser.ai.ai_engine.consentimiento.entity.*;
import com.renaser.ai.ai_engine.consentimiento.repository.*;
import com.renaser.ai.ai_engine.usuario.entity.*;
import com.renaser.ai.ai_engine.usuario.repository.*;
import com.renaser.ai.ai_engine.organizacion.entity.*;
import com.renaser.ai.ai_engine.organizacion.repository.*;
import com.renaser.ai.ai_engine.administracion.service.ServicioAdministracion;
import com.renaser.ai.ai_engine.administracion.dto.DtosAdministracion.*;
import com.renaser.ai.ai_engine.postulacion.entity.*;
import com.renaser.ai.ai_engine.postulacion.repository.*;
import com.renaser.ai.ai_engine.postulacion.service.*;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServicioAdministracionImpl implements ServicioAdministracion {

    /** El permiso que abre la puerta de los permisos: el único que no puede quedarse sin nadie. */
    private static final String ADMINISTRAR_PERMISOS = "administrar_permisos";

    private final ParametroRepository parametros;
    private final PlantillaCorreoRepository plantillas;
    private final AuditoriaRepository auditorias;
    private final SolicitudBorradoRepository solicitudesBorrado;
    private final PersonaRepository personas;
    private final com.renaser.ai.ai_engine.perfil.service.ServicioCicloVidaPerfil cicloVidaPerfil;
    private final UsuarioRepository usuarios;
    private final AreaRepository areas;
    private final RolRepository roles;
    private final UsuarioRolRepository usuarioRoles;
    private final PermisoRepository permisos;
    private final RolPermisoRepository rolPermisos;
    private final ConsentimientoRepository consentimientos;
    private final PostulacionRepository postulaciones;
    private final EstadoPostulacionRepository estados;
    private final CvRepository cvs;
    private final EnlaceCvRepository enlaces;
    private final ArchivoRepository archivos;
    private final CorreoEnviadoRepository correosEnviados;
    private final AlmacenArchivos almacen;
    private final MaquinaEstados maquina;
    private final ServicioCorreo correo;
    private final ServicioAuditoria auditoria;

    // ============ Parámetros ============

    @Override
    public List<ParametroPanel> parametros(ContextoUsuario quien) {
        return parametros.findByOrganizacionIdOrderByCodigo(quien.organizacionId()).stream()
                .map(p -> new ParametroPanel(p.getCodigo(), p.getValor(), p.getTipo(), p.getDescripcion()))
                .toList();
    }

    @Override
    @Transactional
    public void editarParametro(ContextoUsuario quien, String codigo, String valor, String motivo) {
        Parametro parametro = parametros.findByOrganizacionIdAndCodigo(quien.organizacionId(), codigo)
                .orElseThrow(() -> new ResourceNotFoundException("Parámetro", "código", codigo));
        if ("ENTERO".equals(parametro.getTipo())) {
            try {
                Integer.parseInt(valor.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("El parámetro «" + codigo + "» espera un número entero");
            }
        }
        String anterior = parametro.getValor();
        parametro.setValor(valor.trim());
        parametro.setModificadoPorUsuarioId(quien.usuarioId());
        parametro.setModificadoEn(Instant.now());
        parametros.save(parametro);
        auditoria.registrar(quien.organizacionId(), quien, "editar_parametro",
                "parametro", parametro.getId(),
                Map.of("valor", anterior), Map.of("valor", valor.trim()), motivo);
    }

    // ============ Plantillas de correo ============

    @Override
    public List<PlantillaPanel> plantillas(ContextoUsuario quien) {
        return plantillas.findByOrganizacionIdOrderByCodigoAscVersionDesc(quien.organizacionId()).stream()
                .map(p -> new PlantillaPanel(p.getId(), p.getCodigo(), p.getVersion(),
                        p.getAsunto(), p.getCuerpo(), p.isEsActiva()))
                .toList();
    }

    @Override
    @Transactional
    public Long nuevaVersionPlantilla(ContextoUsuario quien, NuevaPlantilla datos) {
        int siguienteVersion = plantillas
                .findFirstByOrganizacionIdAndCodigoOrderByVersionDesc(quien.organizacionId(), datos.codigo())
                .map(p -> p.getVersion() + 1)
                .orElse(1);

        // La versión anterior se desactiva pero no se toca: los correos ya enviados
        // guardan su código y versión, y siguen explicados
        plantillas.findFirstByOrganizacionIdAndCodigoAndEsActivaTrueOrderByVersionDesc(
                        quien.organizacionId(), datos.codigo())
                .ifPresent(anterior -> {
                    anterior.setEsActiva(false);
                    plantillas.save(anterior);
                });

        PlantillaCorreo nueva = plantillas.save(PlantillaCorreo.builder()
                .organizacionId(quien.organizacionId())
                .codigo(datos.codigo())
                .version(siguienteVersion)
                .asunto(datos.asunto())
                .cuerpo(datos.cuerpo())
                .esActiva(true)
                .creadoEn(Instant.now())
                .build());
        auditoria.registrar(quien.organizacionId(), quien, "editar_texto_correo",
                "plantilla_correo", nueva.getId(), null,
                Map.of("codigo", datos.codigo(), "version", siguienteVersion), null);
        return nueva.getId();
    }

    // ============ Auditoría ============

    @Override
    public Page<FilaAuditoria> auditoria(ContextoUsuario quien, String entidad, int pagina, int tamano) {
        PageRequest pagina_ = PageRequest.of(pagina, Math.min(tamano, 200));
        Page<Auditoria> filas = (entidad == null || entidad.isBlank())
                ? auditorias.findByOrganizacionIdOrderByOcurridaEnDesc(quien.organizacionId(), pagina_)
                : auditorias.findByOrganizacionIdAndEntidadOrderByOcurridaEnDesc(
                        quien.organizacionId(), entidad, pagina_);
        return filas.map(a -> new FilaAuditoria(a.getId(), a.getAccion(), a.getEntidad(),
                a.getEntidadId(), a.getUsuarioId(), a.getMotivo(), a.getOcurridaEn()));
    }

    // ============ Borrado de datos ============

    @Override
    public List<SolicitudBorradoPanel> solicitudesBorradoPendientes(ContextoUsuario quien) {
        return solicitudesBorrado.findByEjecutadoEnIsNullOrderBySolicitadoEnAsc().stream()
                .map(s -> new SolicitudBorradoPanel(s.getId(), s.getPersonaId(), s.getMotivo(),
                        s.getSolicitadoEn(), s.getEjecutadoEn()))
                .toList();
    }

    // La anonimización completa, en el orden que importa: primero el aviso (todavía hay
    // correo), después el vaciado, y la trazabilidad queda intacta pero sin nombre.
    @Override
    @Transactional
    public void ejecutarBorrado(ContextoUsuario quien, Long solicitudId) {
        SolicitudBorrado solicitud = solicitudesBorrado.findById(solicitudId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de borrado", "id", solicitudId));
        if (solicitud.getEjecutadoEn() != null) {
            throw new IllegalStateException("Esta solicitud ya fue ejecutada");
        }
        Persona persona = personas.findById(solicitud.getPersonaId())
                .orElseThrow(() -> new ResourceNotFoundException("Persona", "id", solicitud.getPersonaId()));
        Usuario usuario = usuarios.findFirstByPersonaId(persona.getId()).orElse(null);

        // 1 · El aviso sale antes del vaciado, mientras el correo todavía existe
        if (usuario != null && usuario.getCorreo() != null) {
            correo.enviar(quien.organizacionId(), usuario.getId(), usuario.getCorreo(),
                    "BORRADO_EJECUTADO", Map.of());
        }

        // 2 · Las postulaciones activas se cierran con su motivo
        if (usuario != null) {
            for (Postulacion p : postulaciones.findByUsuarioIdOrderByCreadoEnDesc(usuario.getId())) {
                boolean esFinal = estados.findById(p.getEstadoCodigo())
                        .map(EstadoPostulacion::isEsFinal).orElse(true);
                if (!esFinal) {
                    maquina.transicionar(p, "CERRADA", quien,
                            "La persona pidió el borrado de sus datos", false, false, "BORRADO_DATOS");
                }
                // 3 · El CV: el archivo físico se borra, el texto y los enlaces se vacían
                cvs.findByPostulacionId(p.getId()).ifPresent(cv -> {
                    if (cv.getArchivoOriginalId() != null) {
                        archivos.findById(cv.getArchivoOriginalId()).ifPresent(almacen::borrarContenido);
                    }
                    cv.setTextoExtraido(null);
                    cv.setResultadoOrgulloso(null);
                    cvs.save(cv);
                    enlaces.findByCvId(cv.getId()).forEach(e -> {
                        e.setUrl("[eliminado por solicitud de borrado]");
                        enlaces.save(e);
                    });
                });
            }
        }

        // 4 · Los correos enviados conservan la fila pero pierden el contenido personal
        if (usuario != null) {
            correosEnviados.findByUsuarioIdOrderByEnviadoEnDesc(usuario.getId()).forEach(c -> {
                c.setAsunto("[eliminado por solicitud de borrado]");
                c.setCuerpo("[eliminado por solicitud de borrado]");
                correosEnviados.save(c);
            });
        }

        // 5 · El consentimiento pierde el nombre registrado
        consentimientos.findByPersonaId(persona.getId()).forEach(c -> {
            c.setNombreRegistrado(null);
            consentimientos.save(c);
        });

        // 5b · El perfil del candidato se borra entero, y de verdad — no se anonimiza.
        // No sostiene ninguna decisión (no puntúa), así que no hay nada que conservar.
        // Antes de vaciar la persona, porque se localiza por persona_id.
        cicloVidaPerfil.borrarPorPersona(persona.getId());

        // 6 · La persona queda vacía, la cuenta sin correo y desactivada
        persona.setNombre(null);
        persona.setApellidos(null);
        persona.setTelefono(null);
        persona.setDocumento(null);
        persona.setFechaNacimiento(null);
        persona.setAnonimizadoEn(Instant.now());
        personas.save(persona);
        if (usuario != null) {
            usuario.setCorreo(null);
            usuario.setEsActivo(false);
            usuarios.save(usuario);
        }

        solicitud.setEjecutadoEn(Instant.now());
        solicitud.setEjecutadoPorUsuarioId(quien.usuarioId());
        solicitudesBorrado.save(solicitud);

        auditoria.registrar(quien.organizacionId(), quien, "ejecutar_borrado_datos",
                "persona", persona.getId(), null, Map.of("anonimizada", true), solicitud.getMotivo());
    }

    // ============ Usuarios del equipo y roles ============

    @Override
    public List<UsuarioPanel> usuariosEquipo(ContextoUsuario quien) {
        return usuarios.findByOrganizacionIdAndUsuarioRenaserOsIdIsNotNull(quien.organizacionId()).stream()
                .map(this::comoPanel)
                .toList();
    }

    @Override
    @Transactional
    public Long crearUsuarioEquipo(ContextoUsuario quien, CrearUsuarioEquipo datos) {
        usuarios.buscarPorCorreo(quien.organizacionId(), datos.correo()).ifPresent(u -> {
            throw new IllegalStateException("Ya existe una cuenta con ese correo");
        });
        Persona persona = personas.save(Persona.builder()
                .nombre(datos.nombre()).apellidos(datos.apellidos()).creadoEn(Instant.now())
                .build());
        Usuario usuario = usuarios.save(Usuario.builder()
                .organizacionId(quien.organizacionId())
                .personaId(persona.getId())
                .correo(datos.correo().trim().toLowerCase())
                .usuarioRenaserOsId(datos.usuarioRenaserOsId())
                .areaId(datos.areaId())
                .esActivo(true)
                .creadoEn(Instant.now())
                .build());
        asignarRolesInterno(quien, usuario.getId(), datos.roles());
        auditoria.registrar(quien.organizacionId(), quien, "crear_usuario",
                "usuario", usuario.getId(), null,
                Map.of("roles", String.join(",", datos.roles())), null);
        return usuario.getId();
    }

    @Override
    @Transactional
    public void asignarRoles(ContextoUsuario quien, Long usuarioId, List<String> rolesNuevos) {
        usuarios.findById(usuarioId)
                .filter(u -> u.getOrganizacionId().equals(quien.organizacionId()))
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", "id", usuarioId));

        List<UsuarioRol> actuales = usuarioRoles.findByUsuarioId(usuarioId);

        // La regla del último administrador: el sistema no puede quedarse sin nadie
        // que administre. Se comprueba antes de tocar nada.
        Rol admin = roles.findByOrganizacionIdAndCodigo(quien.organizacionId(), "ADMINISTRADOR").orElse(null);
        if (admin != null && !rolesNuevos.contains("ADMINISTRADOR")) {
            boolean loTenia = actuales.stream().anyMatch(ur -> ur.getRolId().equals(admin.getId()));
            if (loTenia && usuarioRoles.countByRolId(admin.getId()) <= 1) {
                throw new IllegalStateException("No se puede quitar el último administrador del sistema");
            }
        }

        List<String> anteriores = actuales.stream()
                .map(ur -> roles.findById(ur.getRolId()).map(Rol::getCodigo).orElse("?"))
                .toList();
        usuarioRoles.deleteAll(actuales);
        asignarRolesInterno(quien, usuarioId, rolesNuevos);

        auditoria.registrar(quien.organizacionId(), quien, "asignar_roles",
                "usuario", usuarioId,
                Map.of("roles", String.join(",", anteriores)),
                Map.of("roles", String.join(",", rolesNuevos)), null);
    }

    @Override
    public List<RolPanel> roles(ContextoUsuario quien) {
        return roles.findByOrganizacionIdOrderByCodigo(quien.organizacionId()).stream()
                .map(r -> new RolPanel(r.getId(), r.getCodigo(), r.getNombre(), r.isEsSistema()))
                .toList();
    }

    // ============ Qué puede cada rol ============

    @Override
    public List<PermisoDelRol> permisosDelRol(ContextoUsuario quien, Long rolId) {
        Rol rol = elRolDeSuOrganizacion(quien, rolId);
        Map<Long, String> concedidos = rolPermisos.findByRolId(rol.getId()).stream()
                .collect(Collectors.toMap(RolPermiso::getPermisoId, RolPermiso::getAlcance));

        return permisos.findAllByOrderByGrupoAscOrdenAsc().stream()
                .map(p -> new PermisoDelRol(p.getCodigo(), p.getEtiqueta(), p.getGrupo(),
                        p.getOrden(), concedidos.get(p.getId())))
                .toList();
    }

    @Override
    @Transactional
    public void concederPermiso(ContextoUsuario quien, Long rolId, String codigoPermiso,
                                ConcederPermiso datos) {
        Rol rol = elRolDeSuOrganizacion(quien, rolId);
        Permiso permiso = elPermiso(codigoPermiso);

        RolPermiso.Clave clave = new RolPermiso.Clave(rol.getId(), permiso.getId());
        RolPermiso existente = rolPermisos.findById(clave).orElse(null);
        String anterior = existente == null ? null : existente.getAlcance();
        if (datos.alcance().equals(anterior)) {
            return;
        }

        rolPermisos.save(RolPermiso.builder()
                .rolId(rol.getId())
                .permisoId(permiso.getId())
                .alcance(datos.alcance())
                // Cambiar el alcance no vuelve a conceder el permiso: si ya estaba, la fecha
                // de cuando se concedió se queda como estaba. Cuándo cambió lo dice la
                // auditoría, que es donde vive la historia.
                .creadoEn(existente == null ? Instant.now() : existente.getCreadoEn())
                .build());

        auditoria.registrar(quien.organizacionId(), quien, "conceder_permiso",
                "rol", rol.getId(),
                anterior == null ? null : Map.of("permiso", codigoPermiso, "alcance", anterior),
                Map.of("permiso", codigoPermiso, "alcance", datos.alcance()), datos.motivo());
    }

    @Override
    @Transactional
    public void revocarPermiso(ContextoUsuario quien, Long rolId, String codigoPermiso,
                               String motivo) {
        Rol rol = elRolDeSuOrganizacion(quien, rolId);
        Permiso permiso = elPermiso(codigoPermiso);

        RolPermiso.Clave clave = new RolPermiso.Clave(rol.getId(), permiso.getId());
        RolPermiso actual = rolPermisos.findById(clave).orElse(null);
        if (actual == null) {
            return;
        }

        // La misma regla del último administrador, aplicada al permiso que administra los
        // permisos: si este es el último rol que lo tiene, quitarlo deja el reparto sin nadie
        // que pueda volver a tocarlo y de ahí solo se sale entrando a la base a mano.
        if (ADMINISTRAR_PERMISOS.equals(codigoPermiso)
                && rolPermisos.contarEnOrganizacion(permiso.getId(), quien.organizacionId()) <= 1) {
            throw new IllegalStateException("No se puede revocar el último «"
                    + ADMINISTRAR_PERMISOS + "»: nadie podría volver a cambiar los permisos");
        }

        rolPermisos.delete(actual);

        auditoria.registrar(quien.organizacionId(), quien, "revocar_permiso",
                "rol", rol.getId(),
                Map.of("permiso", codigoPermiso, "alcance", actual.getAlcance()),
                null, motivo);
    }

    private Rol elRolDeSuOrganizacion(ContextoUsuario quien, Long rolId) {
        return roles.findById(rolId)
                .filter(r -> r.getOrganizacionId().equals(quien.organizacionId()))
                .orElseThrow(() -> new ResourceNotFoundException("Rol", "id", rolId));
    }

    /**
     * El permiso del catálogo, que es global y solo crece con una migración.
     *
     * <p>Un código que no existe se rechaza aquí y no en la base: la clave ajena daría un
     * 500 con el nombre de una restricción dentro, y lo que ha pasado es que alguien pidió
     * un permiso que no está.
     */
    private Permiso elPermiso(String codigo) {
        return permisos.findByCodigo(codigo)
                .orElseThrow(() -> new ResourceNotFoundException("Permiso", "código", codigo));
    }

    @Override
    public List<AreaPanel> areas(ContextoUsuario quien) {
        return areas.findByOrganizacionIdAndEsActivaTrueOrderByNombre(quien.organizacionId()).stream()
                .map(a -> new AreaPanel(a.getId(), a.getNombre(), a.isEsActiva()))
                .toList();
    }

    @Override
    @Transactional
    public Long crearArea(ContextoUsuario quien, String nombre) {
        Area area = areas.save(Area.builder()
                .organizacionId(quien.organizacionId())
                .nombre(nombre.trim())
                .esActiva(true)
                .creadoEn(Instant.now())
                .build());
        auditoria.registrar(quien.organizacionId(), quien, "crear_area",
                "area", area.getId(), null, Map.of("nombre", nombre.trim()), null);
        return area.getId();
    }

    private void asignarRolesInterno(ContextoUsuario quien, Long usuarioId, List<String> codigos) {
        for (String codigo : codigos) {
            Rol rol = roles.findByOrganizacionIdAndCodigo(quien.organizacionId(), codigo)
                    .orElseThrow(() -> new ResourceNotFoundException("Rol", "código", codigo));
            usuarioRoles.save(UsuarioRol.builder()
                    .usuarioId(usuarioId).rolId(rol.getId())
                    .asignadoPorUsuarioId(quien.usuarioId())
                    .creadoEn(Instant.now())
                    .build());
        }
    }

    private UsuarioPanel comoPanel(Usuario u) {
        List<String> codigos = usuarioRoles.findByUsuarioId(u.getId()).stream()
                .map(ur -> roles.findById(ur.getRolId()).map(Rol::getCodigo).orElse("?"))
                .toList();
        return new UsuarioPanel(u.getId(), u.getCorreo(), u.getUsuarioRenaserOsId(),
                u.getAreaId(), u.isEsActivo(), codigos);
    }
}
