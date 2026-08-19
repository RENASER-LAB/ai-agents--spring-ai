package com.renaser.ai.ai_engine.portal.service.impl;

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
import com.renaser.ai.ai_engine.portal.service.ServicioPortal;
import com.renaser.ai.ai_engine.portal.dto.DtosPortal.*;
import com.renaser.ai.ai_engine.postulacion.entity.*;
import com.renaser.ai.ai_engine.postulacion.repository.*;
import com.renaser.ai.ai_engine.postulacion.service.*;
import com.renaser.ai.ai_engine.seguridad.config.*;
import com.renaser.ai.ai_engine.seguridad.dto.*;
import com.renaser.ai.ai_engine.seguridad.exception.CredencialesInvalidasException;
import com.renaser.ai.ai_engine.seguridad.exception.DemasiadosIntentosException;
import com.renaser.ai.ai_engine.seguridad.service.*;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioEvaluacion;
import com.renaser.ai.ai_engine.vacante.entity.*;
import com.renaser.ai.ai_engine.vacante.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ServicioPortalImpl implements ServicioPortal {

    private final OrganizacionRepository organizaciones;
    private final PersonaRepository personas;
    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final UsuarioRolRepository usuarioRoles;
    private final TextoConsentimientoRepository textosConsentimiento;
    private final ConsentimientoRepository consentimientos;
    private final SolicitudBorradoRepository solicitudesBorrado;
    private final VacanteRepository vacantes;
    private final PuestoRepository puestos;
    private final RequisitoObjetivoRepository requisitos;
    private final ServicioEvaluacion evaluaciones;
    private final PostulacionRepository postulaciones;
    private final TransicionEstadoRepository transiciones;
    private final EstadoPostulacionRepository estados;
    private final CvRepository cvs;
    private final EnlaceCvRepository enlaces;
    private final MaquinaEstados maquina;
    private final AlmacenArchivos almacen;
    private final ServicioCorreo correo;
    private final ServicioAuditoria auditoria;
    private final ServicioParametros parametros;
    private final ServicioToken tokens;
    private final IntentosLogin intentos;
    private final PasswordEncoder codificador;

    // El portal es de una organización: la única que existe hoy. Cuando entren los
    // clientes de consultoría, la organización saldrá del dominio o de la ruta.
    private Organizacion organizacion() {
        return organizaciones.findByCodigo("RENASER")
                .orElseThrow(() -> new IllegalStateException("Falta la organización semilla RENASER"));
    }

    // ============ Vacantes públicas ============

    @Override
    public List<VacantePublica> vacantesPublicadas() {
        return vacantes.findByOrganizacionIdAndEstadoOrderByPublicadaEnDesc(organizacion().getId(), "PUBLICADA")
                .stream().map(this::comoPublica).toList();
    }

    @Override
    public VacantePublica vacante(Long id) {
        Vacante vacante = vacantes.findByIdAndOrganizacionId(id, organizacion().getId())
                .filter(v -> "PUBLICADA".equals(v.getEstado()))
                .orElseThrow(() -> new ResourceNotFoundException("Vacante", "id", id));
        return comoPublica(vacante);
    }

    private VacantePublica comoPublica(Vacante v) {
        List<RequisitoPublico> reqs = requisitos.findByVacanteIdAndEsActivoTrue(v.getId()).stream()
                .map(r -> new RequisitoPublico(r.getId(), r.getDescripcion()))
                .toList();
        return new VacantePublica(v.getId(), v.getTitulo(), v.getDescripcion(), v.getProposito(),
                v.getResponsabilidades(), v.getRequisitos(), v.getModalidad(), v.getHorario(),
                v.getUbicacion(), v.getCompensacionPublica(), reqs);
    }

    // ============ Cuenta y consentimientos ============

    @Override
    public List<TextoConsentimientoPublico> textosDeConsentimiento() {
        Long org = organizacion().getId();
        return List.of("PROCESO", "FUTUROS_CONTACTOS").stream()
                .map(tipo -> textosConsentimiento
                        .findFirstByOrganizacionIdAndTipoAndPublicadoEnIsNotNullOrderByPublicadoEnDesc(org, tipo))
                .flatMap(Optional::stream)
                .map(t -> new TextoConsentimientoPublico(t.getTipo(), t.getVersion(), t.getTexto()))
                .toList();
    }

    @Override
    @Transactional
    public void crearCuenta(CrearCuenta datos, String ip, String userAgent) {
        // Sin aceptar el tratamiento de datos no hay cuenta: no es una casilla decorativa
        if (!Boolean.TRUE.equals(datos.aceptaProceso())) {
            throw new IllegalArgumentException("Hay que aceptar el tratamiento de datos personales para crear la cuenta");
        }
        Organizacion org = organizacion();
        usuarios.buscarPorCorreo(org.getId(), datos.correo()).ifPresent(u -> {
            throw new IllegalStateException("Ya existe una cuenta con ese correo");
        });

        Persona persona = personas.save(Persona.builder()
                .nombre(datos.nombre())
                .apellidos(datos.apellidos())
                .creadoEn(Instant.now())
                .build());

        Usuario usuario = usuarios.save(Usuario.builder()
                .organizacionId(org.getId())
                .personaId(persona.getId())
                .correo(datos.correo().trim().toLowerCase())
                .contrasenaHash(codificador.encode(datos.contrasena()))
                .esActivo(true)
                .creadoEn(Instant.now())
                .build());

        roles.findByOrganizacionIdAndCodigo(org.getId(), "CANDIDATO").ifPresent(rol ->
                usuarioRoles.save(UsuarioRol.builder()
                        .usuarioId(usuario.getId()).rolId(rol.getId()).creadoEn(Instant.now())
                        .build()));

        // El consentimiento del proceso es obligatorio; el de futuros contactos, opcional.
        // De cada uno queda la versión exacta del texto, la IP y el navegador.
        registrarConsentimiento(persona, org.getId(), "PROCESO", datos, ip, userAgent);
        if (Boolean.TRUE.equals(datos.aceptaFuturosContactos())) {
            registrarConsentimiento(persona, org.getId(), "FUTUROS_CONTACTOS", datos, ip, userAgent);
        }

        correo.enviar(org.getId(), usuario.getId(), usuario.getCorreo(), "CUENTA_CREADA",
                Map.of("nombre", datos.nombre()));
    }

    private void registrarConsentimiento(Persona persona, Long orgId, String tipo,
                                         CrearCuenta datos, String ip, String userAgent) {
        TextoConsentimiento texto = textosConsentimiento
                .findFirstByOrganizacionIdAndTipoAndPublicadoEnIsNotNullOrderByPublicadoEnDesc(orgId, tipo)
                .orElseThrow(() -> new IllegalStateException("No hay texto de consentimiento publicado: " + tipo));
        consentimientos.save(Consentimiento.builder()
                .personaId(persona.getId())
                .textoConsentimientoId(texto.getId())
                .nombreRegistrado(datos.nombre() + " " + datos.apellidos())
                .aceptadoEn(Instant.now())
                .ip(ip)
                .userAgent(userAgent)
                .creadoEn(Instant.now())
                .build());
    }

    @Override
    public Sesion entrar(Login datos) {
        Organizacion org = organizacion();
        long esperaPendiente = intentos.segundosDeBloqueo(datos.correo());
        if (esperaPendiente > 0) {
            throw new DemasiadosIntentosException(esperaPendiente);
        }
        int maximo = parametros.entero(org.getId(), "intentos_login_max", 5);
        int minutosBloqueo = parametros.entero(org.getId(), "minutos_bloqueo_login", 15);

        Usuario usuario = usuarios.buscarPorCorreo(org.getId(), datos.correo())
                .filter(Usuario::isEsActivo)
                .filter(u -> u.getContrasenaHash() != null
                        && codificador.matches(datos.contrasena(), u.getContrasenaHash()))
                .orElse(null);

        if (usuario == null) {
            intentos.registrarFallo(datos.correo(), maximo, minutosBloqueo);
            // El mismo mensaje exista o no el correo: no se regala información
            throw new CredencialesInvalidasException("Correo o contraseña incorrectos");
        }

        intentos.registrarExito(datos.correo());
        usuario.setUltimoAccesoEn(Instant.now());
        usuarios.save(usuario);
        return new Sesion(tokens.emitir(usuario.getId(), org.getId(), "CANDIDATO"), usuario.getId());
    }

    // ============ Postular ============

    @Override
    @Transactional
    public UUID postular(ContextoUsuario quien, Long vacanteId, MultipartFile cv,
                         String resultadoOrgulloso, String portafolio, String linkedin, String github,
                         List<Long> requisitosConfirmados) {
        Vacante vacante = vacantes.findByIdAndOrganizacionId(vacanteId, quien.organizacionId())
                .filter(v -> "PUBLICADA".equals(v.getEstado()))
                .orElseThrow(() -> new ResourceNotFoundException("Vacante", "id", vacanteId));
        if (postulaciones.existsByUsuarioIdAndVacanteId(quien.usuarioId(), vacanteId)) {
            throw new IllegalStateException("Ya postulaste a esta vacante");
        }
        if (resultadoOrgulloso == null || resultadoOrgulloso.isBlank()) {
            throw new IllegalArgumentException("Cuéntanos un resultado del que te sientas orgulloso: es obligatorio");
        }

        // Nace en POSTULADA: el único tramo donde el sistema decide solo, y únicamente
        // contra los requisitos objetivos configurados de antemano
        Postulacion postulacion = postulaciones.save(Postulacion.builder()
                .organizacionId(quien.organizacionId())
                .uuid(UUID.randomUUID())
                .usuarioId(quien.usuarioId())
                .vacanteId(vacanteId)
                .estadoCodigo("POSTULADA")
                .rondasEvidenciaUsadas(0)
                .movidoEn(Instant.now())
                .creadoEn(Instant.now())
                .build());
        transiciones.save(TransicionEstado.builder()
                .postulacionId(postulacion.getId())
                .estadoNuevoCodigo("POSTULADA")
                .esSistema(true).esPorLote(false)
                .ocurridaEn(Instant.now()).creadoEn(Instant.now())
                .build());

        Archivo archivo = almacen.guardar(quien.organizacionId(), cv);
        Cv curriculum = cvs.save(Cv.builder()
                .postulacionId(postulacion.getId())
                .archivoOriginalId(archivo.getId())
                .resultadoOrgulloso(resultadoOrgulloso)
                .creadoEn(Instant.now())
                .build());
        guardarEnlace(curriculum.getId(), portafolio, "PORTAFOLIO");
        guardarEnlace(curriculum.getId(), linkedin, "OTRO");
        guardarEnlace(curriculum.getId(), github, "REPOSITORIO");

        Usuario usuario = usuarios.findById(quien.usuarioId()).orElseThrow();
        String nombre = personas.findById(quien.personaId()).map(Persona::getNombre).orElse("");
        correo.enviar(quien.organizacionId(), usuario.getId(), usuario.getCorreo(), "POSTULACION_RECIBIDA",
                Map.of("nombre", nombre == null ? "" : nombre,
                       "vacante", vacante.getTitulo(),
                       "codigo", postulacion.getUuid().toString()));

        // Autodeclaración: el formulario preguntó por cada requisito activo y aquí llegan
        // los que el candidato confirmó. Cualquier requisito activo no confirmado detiene
        // la postulación, y la regla exacta queda escrita en la transición.
        List<RequisitoObjetivo> activos = requisitos.findByVacanteIdAndEsActivoTrue(vacanteId);
        Set<Long> confirmados = requisitosConfirmados == null
                ? Set.of() : new HashSet<>(requisitosConfirmados);
        List<RequisitoObjetivo> incumplidos = activos.stream()
                .filter(r -> !confirmados.contains(r.getId()))
                .toList();

        if (!incumplidos.isEmpty()) {
            String reglas = String.join(" · ", incumplidos.stream().map(RequisitoObjetivo::getRegla).toList());
            maquina.transicionar(postulacion, "NO_CONTINUA", null,
                    "Requisito objetivo no cumplido: " + reglas, true, false, "REQUISITO_OBJETIVO");
        } else {
            // Su evaluación se crea aquí, no cuando entre a responderla: así queda atada a la
            // versión del banco que estaba publicada el día que postuló. Sin esto la
            // postulación quedaría esperando algo que el candidato no tendría cómo hacer.
            Puesto puesto = puestos.findById(vacante.getPuestoId())
                    .orElseThrow(() -> new IllegalStateException(
                            "La vacante apunta a un puesto que no existe"));
            postulacion.setEvaluacionId(evaluaciones.crearAlPostular(
                    quien.organizacionId(), quien.usuarioId(),
                    vacante.getPlantillaEvaluacionId(), puesto.getNivelPuestoCodigo()));
            postulaciones.save(postulacion);

            maquina.transicionar(postulacion, "PERFIL_TURNO_CANDIDATO", null, null, true, false, null);
        }
        return postulacion.getUuid();
    }

    private void guardarEnlace(Long cvId, String url, String tipo) {
        if (url != null && !url.isBlank()) {
            enlaces.save(EnlaceCv.builder().cvId(cvId).url(url.trim()).tipo(tipo).creadoEn(Instant.now()).build());
        }
    }

    // ============ Mis postulaciones ============

    @Override
    public List<MiPostulacion> misPostulaciones(ContextoUsuario quien) {
        return postulaciones.findByUsuarioIdOrderByCreadoEnDesc(quien.usuarioId()).stream()
                .map(this::comoResumen)
                .toList();
    }

    @Override
    public MiPostulacionDetalle miPostulacion(ContextoUsuario quien, UUID uuid) {
        Postulacion postulacion = laMia(quien, uuid);
        List<PasoHistorial> historial = transiciones
                .findByPostulacionIdOrderByOcurridaEnAsc(postulacion.getId()).stream()
                .map(t -> new PasoHistorial(t.getEstadoAnteriorCodigo(), t.getEstadoNuevoCodigo(),
                        t.isEsSistema(), t.getOcurridaEn()))
                .toList();
        return new MiPostulacionDetalle(comoResumen(postulacion), historial);
    }

    @Override
    @Transactional
    public void retirar(ContextoUsuario quien, UUID uuid) {
        Postulacion postulacion = laMia(quien, uuid);
        EstadoPostulacion estado = estados.findById(postulacion.getEstadoCodigo()).orElseThrow();
        if (estado.isEsFinal()) {
            throw new IllegalStateException("Esta postulación ya terminó: no se puede retirar");
        }
        // Retirarse NO borra los datos: eso se pide aparte, y es otra cosa
        maquina.transicionar(postulacion, "CERRADA", quien,
                "El candidato retiró su postulación desde el portal", false, false, "RETIRO_CANDIDATO");
    }

    @Override
    @Transactional
    public void retirarConsentimientoFuturos(ContextoUsuario quien) {
        Consentimiento vigente = consentimientos.vigenteDeTipo(quien.personaId(), "FUTUROS_CONTACTOS")
                .orElseThrow(() -> new IllegalStateException("No tienes un consentimiento de futuros contactos vigente"));
        vigente.setRetiradoEn(Instant.now());
        consentimientos.save(vigente);
        auditoria.registrar(quien.organizacionId(), quien, "retiro_consentimiento_futuros",
                "consentimiento", vigente.getId(), null, Map.of("retirado", true), null);
    }

    @Override
    @Transactional
    public void pedirBorrado(ContextoUsuario quien, String motivo) {
        if (solicitudesBorrado.existsByPersonaIdAndEjecutadoEnIsNull(quien.personaId())) {
            throw new IllegalStateException("Ya tienes una solicitud de borrado pendiente");
        }
        SolicitudBorrado solicitud = solicitudesBorrado.save(SolicitudBorrado.builder()
                .personaId(quien.personaId())
                .motivo(motivo)
                .solicitadoEn(Instant.now())
                .creadoEn(Instant.now())
                .build());
        auditoria.registrar(quien.organizacionId(), quien, "solicitud_borrado",
                "solicitud_borrado", solicitud.getId(), null, Map.of("solicitado", true), motivo);
    }

    // ============ ayudas ============

    private Postulacion laMia(ContextoUsuario quien, UUID uuid) {
        return postulaciones.findByUuid(uuid)
                .filter(p -> p.getUsuarioId().equals(quien.usuarioId()))
                .orElseThrow(() -> new ResourceNotFoundException("Postulación", "código", uuid));
    }

    private MiPostulacion comoResumen(Postulacion p) {
        String titulo = vacantes.findById(p.getVacanteId()).map(Vacante::getTitulo).orElse("");
        String nombreEstado = estados.findById(p.getEstadoCodigo())
                .map(EstadoPostulacion::getNombre).orElse(p.getEstadoCodigo());
        long dias = Duration.between(p.getMovidoEn(), Instant.now()).toDays();
        return new MiPostulacion(p.getUuid().toString(), titulo, p.getEstadoCodigo(), nombreEstado,
                p.getGrupoPrioridad(), dias, p.getCreadoEn());
    }
}
