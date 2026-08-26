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
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final com.renaser.ai.ai_engine.perfil.service.ServicioPropuestaPerfil propuestaPerfil;
    private final com.renaser.ai.ai_engine.perfil.service.ServicioLecturaCv lecturaCv;
    private final AlmacenArchivos almacen;
    private final ServicioCorreo correo;
    private final ServicioAuditoria auditoria;
    private final ServicioParametros parametros;
    private final ServicioToken tokens;
    private final IntentosLogin intentos;
    private final PasswordEncoder codificador;

    // El candidato es DE LA PLATAFORMA: una sola cuenta, y con ella postula a la vacante
    // de cualquier empresa. Su cuenta, sus consentimientos y su login cuelgan de la
    // organización plataforma; lo único del portal que cruza empresas es el tablón de
    // vacantes, y su postulación nace en la empresa de la vacante.
    private Organizacion plataforma() {
        return organizaciones.findByEsPlataformaTrue()
                .orElseThrow(() -> new IllegalStateException(
                        "Ninguna organización está marcada como plataforma"));
    }

    // ============ Vacantes públicas ============

    @Override
    public List<VacantePublica> vacantesPublicadas() {
        // Las publicadas de TODAS las empresas juntas: la excepción deliberada de la
        // pieza B, con nombre y apellido — el tablón es lo que hace plataforma a la
        // plataforma. Cada vacante dice de qué empresa es, porque el candidato tiene que
        // saber a quién le manda su currículum.
        List<Vacante> publicadas = vacantes.findByEstadoOrderByPublicadaEnDesc("PUBLICADA");

        // Los requisitos de todas de una vez. Este es el tablón de empleo: la única pantalla
        // que se sirve sin haber entrado y, por eso, la que más veces se pide. Una consulta
        // por vacante aquí no la paga un candidato, la paga cada visita.
        Map<Long, List<RequisitoObjetivo>> porVacante = requisitosDe(
                publicadas.stream().map(Vacante::getId).toList());
        Map<Long, String> nombrePorOrganizacion = nombresDeOrganizacion(publicadas);

        return publicadas.stream()
                .map(v -> comoPublica(v, porVacante.getOrDefault(v.getId(), List.of()),
                        nombrePorOrganizacion.getOrDefault(v.getOrganizacionId(), "")))
                .toList();
    }

    @Override
    public VacantePublica vacante(Long id) {
        // Sin filtro de organización a propósito: el tablón es de todas las empresas.
        // Lo que sí se exige es que esté PUBLICADA — un borrador no existe para nadie.
        Vacante vacante = vacantes.findById(id)
                .filter(v -> "PUBLICADA".equals(v.getEstado()))
                .orElseThrow(() -> new ResourceNotFoundException("Vacante", "id", id));
        String nombreEmpresa = organizaciones.findById(vacante.getOrganizacionId())
                .map(Organizacion::getNombre).orElse("");
        return comoPublica(vacante, requisitos.findByVacanteIdAndEsActivoTrue(vacante.getId()),
                nombreEmpresa);
    }

    private Map<Long, String> nombresDeOrganizacion(List<Vacante> deVacantes) {
        List<Long> ids = deVacantes.stream().map(Vacante::getOrganizacionId).distinct().toList();
        return organizaciones.findAllById(ids).stream()
                .collect(Collectors.toMap(Organizacion::getId, Organizacion::getNombre));
    }

    /**
     * Los requisitos activos de un lote de vacantes, agrupados por la suya.
     *
     * <p>Ordenados por id porque la consulta no lo pedía y la base no lo promete: al traerlos
     * en bloque llegan mezclados de todas las vacantes, y sin un orden explícito la misma
     * vacante podría enseñar sus requisitos en distinto orden en dos visitas seguidas.
     */
    private Map<Long, List<RequisitoObjetivo>> requisitosDe(List<Long> vacanteIds) {
        if (vacanteIds.isEmpty()) {
            return Map.of();
        }
        return requisitos.findByVacanteIdInAndEsActivoTrue(vacanteIds).stream()
                .sorted(Comparator.comparing(RequisitoObjetivo::getId))
                .collect(Collectors.groupingBy(RequisitoObjetivo::getVacanteId));
    }

    private VacantePublica comoPublica(Vacante v, List<RequisitoObjetivo> suyos, String nombreEmpresa) {
        List<RequisitoPublico> reqs = suyos.stream()
                .map(r -> new RequisitoPublico(r.getId(), r.getDescripcion()))
                .toList();
        return new VacantePublica(v.getId(), v.getTitulo(), nombreEmpresa, v.getDescripcion(),
                v.getProposito(), v.getResponsabilidades(), v.getRequisitos(), v.getModalidad(),
                v.getHorario(), v.getUbicacion(), v.getCompensacionPublica(), reqs);
    }

    // ============ Cuenta y consentimientos ============

    @Override
    public List<TextoConsentimientoPublico> textosDeConsentimiento() {
        Long org = plataforma().getId();
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
        Organizacion org = plataforma();
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
        Organizacion org = plataforma();
        long esperaPendiente = intentos.segundosDeBloqueo(datos.correo());
        if (esperaPendiente > 0) {
            throw new DemasiadosIntentosException(esperaPendiente);
        }
        int maximo = parametros.entero(org.getId(), "intentos_login_max", 5);
        int minutosBloqueo = parametros.entero(org.getId(), "minutos_bloqueo_login", 15);

        Usuario usuario = usuarios.buscarPorCorreo(org.getId(), datos.correo())
                // La línea simétrica a la del panel: una cuenta de equipo no es un
                // candidato. Desde la V37 el equipo también tiene contraseña, y sin este
                // filtro la gente del panel de la plataforma abría el portal como
                // candidata — dos mundos con la misma llave.
                .filter(u -> !u.isEsEquipo())
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
        // El candidato postula a la vacante de cualquier empresa: la vacante se busca en
        // el tablón entero, no en la organización del candidato (que es la plataforma).
        Vacante vacante = vacantes.findById(vacanteId)
                .filter(v -> "PUBLICADA".equals(v.getEstado()))
                .orElseThrow(() -> new ResourceNotFoundException("Vacante", "id", vacanteId));
        if (postulaciones.existsByUsuarioIdAndVacanteId(quien.usuarioId(), vacanteId)) {
            throw new IllegalStateException("Ya postulaste a esta vacante");
        }
        if (resultadoOrgulloso == null || resultadoOrgulloso.isBlank()) {
            throw new IllegalArgumentException("Cuéntanos un resultado del que te sientas orgulloso: es obligatorio");
        }

        // La postulación nace en la organización DE LA VACANTE, no en la del candidato:
        // es lo que hace que el panel de cada empresa vea a sus candidatos y que el
        // aislamiento signifique algo. El mismo criterio arrastra todo lo que el panel de
        // la empresa debe ver: el CV archivado, la evaluación y sus avisos.
        Long organizacionDeLaVacante = vacante.getOrganizacionId();

        // Nace en POSTULADA: el único tramo donde el sistema decide solo, y únicamente
        // contra los requisitos objetivos configurados de antemano
        Postulacion postulacion = postulaciones.save(Postulacion.builder()
                .organizacionId(organizacionDeLaVacante)
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

        Archivo archivo = almacen.guardar(organizacionDeLaVacante, cv);
        Cv curriculum = cvs.save(Cv.builder()
                .postulacionId(postulacion.getId())
                .archivoOriginalId(archivo.getId())
                .resultadoOrgulloso(resultadoOrgulloso)
                .creadoEn(Instant.now())
                .build());
        guardarEnlace(curriculum.getId(), portafolio, "PORTAFOLIO");
        guardarEnlace(curriculum.getId(), linkedin, "OTRO");
        guardarEnlace(curriculum.getId(), github, "REPOSITORIO");

        // El perfil del candidato se alimenta de lo que acaba de pasar: los enlaces del
        // formulario se le proponen, y el curriculum se manda a leer — o se reutiliza la
        // lectura ya pagada, si esta persona ya postulo con el mismo archivo (RF-161).
        //
        // Van DENTRO de la transaccion de postular, y eso es una decision con precio: la
        // lectura tiene que ver la postulacion recien insertada (sus trabajos y su ficha
        // apuntan a ella por clave foranea), asi que aislarla en una transaccion aparte la
        // dejaria sin nada a lo que apuntar. El precio es que un fallo de base aqui —una
        // carrera por el UNIQUE del perfil entre dos postulaciones a la vez— si tumbaria la
        // postulacion; el try/catch de dentro atrapa el error de logica, no ese.
        propuestaPerfil.proponerEnlaces(quien.personaId(), linkedin, github, portafolio);
        lecturaCv.trasPostular(quien.personaId(), postulacion.getId());

        Usuario usuario = usuarios.findById(quien.usuarioId()).orElseThrow();
        String nombre = personas.findById(quien.personaId()).map(Persona::getNombre).orElse("");
        correo.enviar(organizacionDeLaVacante, usuario.getId(), usuario.getCorreo(), "POSTULACION_RECIBIDA",
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
        } else if (!vacante.isAplicaEvaluacion()) {
            // Esta vacante no lleva banco de preguntas: no hay nada que el candidato deba
            // responder en el Perfil Integral. Va directo a la bandeja del equipo, que
            // decide a quién invitar a la prueba del puesto — su única evaluación.
            maquina.transicionar(postulacion, "PERFIL_POR_CONFIRMAR", null, null, true, false, null);
        } else {
            // Su evaluación se crea aquí, no cuando entre a responderla: así queda atada a la
            // versión del banco que estaba publicada el día que postuló. Sin esto la
            // postulación quedaría esperando algo que el candidato no tendría cómo hacer.
            Puesto puesto = puestos.findById(vacante.getPuestoId())
                    .orElseThrow(() -> new IllegalStateException(
                            "La vacante apunta a un puesto que no existe"));
            postulacion.setEvaluacionId(evaluaciones.crearAlPostular(
                    organizacionDeLaVacante, quien.usuarioId(),
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
        List<Postulacion> mias = postulaciones.findByUsuarioIdOrderByCreadoEnDesc(quien.usuarioId());

        // Son pocas —las de una sola persona—, pero el catálogo de estados son dieciocho filas
        // fijas y se estaba pidiendo una por postulación. Traerlo entero cuesta lo mismo que
        // pedir uno, y así la pantalla no crece en consultas con lo que se postule nadie.
        Map<String, String> nombreEstado = estados.findAllByOrderByOrden().stream()
                .collect(Collectors.toMap(EstadoPostulacion::getCodigo,
                        EstadoPostulacion::getNombre, (a, b) -> a));
        List<Long> vacanteIds = mias.stream().map(Postulacion::getVacanteId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Vacante> porVacante = vacantes.findAllById(vacanteIds).stream()
                .collect(Collectors.toMap(Vacante::getId, Function.identity()));

        return mias.stream().map(p -> comoResumen(p, porVacante, nombreEstado)).toList();
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

    /** Una sola: la vacante y su estado se piden sueltos porque no hay tanda con la que ir. */
    private MiPostulacion comoResumen(Postulacion p) {
        Map<Long, Vacante> unaVacante = vacantes.findById(p.getVacanteId()).stream()
                .collect(Collectors.toMap(Vacante::getId, Function.identity()));
        Map<String, String> unEstado = estados.findById(p.getEstadoCodigo()).stream()
                .collect(Collectors.toMap(EstadoPostulacion::getCodigo, EstadoPostulacion::getNombre));
        return comoResumen(p, unaVacante, unEstado);
    }

    private MiPostulacion comoResumen(Postulacion p, Map<Long, Vacante> porVacante,
                                      Map<String, String> nombrePorEstado) {
        String titulo = Optional.ofNullable(porVacante.get(p.getVacanteId()))
                .map(Vacante::getTitulo).orElse("");
        // Si el código no está en el catálogo se enseña el código, igual que antes: es feo,
        // pero deja ver qué estado es en vez de un hueco en blanco.
        String nombreEstado = nombrePorEstado.getOrDefault(p.getEstadoCodigo(), p.getEstadoCodigo());
        long dias = Duration.between(p.getMovidoEn(), Instant.now()).toDays();
        return new MiPostulacion(p.getUuid().toString(), titulo, p.getEstadoCodigo(), nombreEstado,
                p.getGrupoPrioridad(), dias, p.getCreadoEn());
    }
}
