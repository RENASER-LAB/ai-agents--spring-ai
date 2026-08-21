package com.renaser.ai.ai_engine.simulacion.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacion.*;
import com.renaser.ai.ai_engine.simulacion.entity.*;
import com.renaser.ai.ai_engine.simulacion.repository.*;
import com.renaser.ai.ai_engine.simulacion.service.ServicioDisponibilidadSimulacion;
import com.renaser.ai.ai_engine.simulacion.service.ServicioSimulacion;
import com.renaser.ai.ai_engine.usuario.entity.Rol;
import com.renaser.ai.ai_engine.usuario.entity.UsuarioRol;
import com.renaser.ai.ai_engine.usuario.repository.RolRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRolRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServicioSimulacionImpl implements ServicioSimulacion {

    private static final String ESPERANDO = "SIMULACION_POR_HABILITAR";
    private static final String PUEDE_ELEGIR = "SIMULACION_TURNO_CANDIDATO";
    private static final String POR_CONFIRMAR = "SIMULACION_POR_CONFIRMAR";

    // El reparto de minutos por defecto (RF-96). Cada sesión se queda con una copia, así que
    // cambiarlo aquí no altera las sesiones que ya existen.
    private static final List<Object[]> TRAMOS_POR_DEFECTO = List.of(
            new Object[]{"CONTEXTO", "Contexto", 0, 10},
            new Object[]{"PREGUNTAS", "Preguntas", 10, 20},
            new Object[]{"EJECUCION", "Ejecución", 20, 80},
            new Object[]{"CAMBIO", "Cambio inesperado", 80, 100},
            new Object[]{"ENTREGA", "Entrega", 100, 105},
            new Object[]{"CONVERSACION", "Conversación", 105, 120});

    private final SesionSimulacionRepository sesiones;
    private final SesionVacanteRepository sesionesVacante;
    private final SesionResponsableRepository responsables;
    private final TramoSimulacionRepository tramos;
    private final InformacionCriticaRepository informacionCritica;
    private final InscripcionSesionRepository inscripciones;
    private final MarcaTiempoSimulacionRepository marcas;
    private final PreguntaGeneradaRepository preguntas;
    private final PostulacionRepository postulaciones;
    private final VacanteRepository vacantes;
    private final ColaCalificacionIa cola;
    private final RolRepository roles;
    private final UsuarioRolRepository usuarioRoles;
    private final MaquinaEstados maquina;
    private final ServicioDisponibilidadSimulacion disponibilidad;
    private final ServicioParametros parametros;
    private final ServicioAuditoria auditoria;
    private final Permisos permisos;

    // ============ Administración de sesiones ============

    @Override
    @Transactional
    public Long crearSesion(ContextoUsuario quien, CrearSesion datos) {
        if (datos.vacanteIds().isEmpty()) {
            throw new IllegalArgumentException("Hay que decir para qué vacantes sirve la sesión");
        }
        if (datos.duracionMinutos() > 120) {
            throw new IllegalArgumentException(
                    "Una simulación dura hasta 120 minutos, incluida la conversación final");
        }

        SesionSimulacion sesion = sesiones.save(SesionSimulacion.builder()
                .organizacionId(quien.organizacionId())
                .fechaHora(datos.fechaHora())
                .duracionMinutos(datos.duracionMinutos())
                .modalidad(datos.modalidad())
                .lugar(datos.lugar())
                .enlace(datos.enlace())
                .cupo(datos.cupo())
                .estado("PUBLICADA")
                .enunciado(datos.enunciado())
                .creadaPorUsuarioId(quien.usuarioId())
                .creadoEn(Instant.now())
                .build());

        for (Long vacanteId : datos.vacanteIds()) {
            vacantes.findByIdAndOrganizacionId(vacanteId, quien.organizacionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Vacante", "id", vacanteId));
            sesionesVacante.save(SesionVacante.builder()
                    .sesionSimulacionId(sesion.getId()).vacanteId(vacanteId)
                    .creadoEn(Instant.now()).build());
        }

        for (Object[] t : TRAMOS_POR_DEFECTO) {
            tramos.save(TramoSimulacion.builder()
                    .sesionSimulacionId(sesion.getId())
                    .codigo((String) t[0]).nombre((String) t[1])
                    .minutoInicio((Integer) t[2]).minutoFin((Integer) t[3])
                    .creadoEn(Instant.now()).build());
        }

        auditoria.registrar(quien.organizacionId(), quien, "crear_sesion_simulacion",
                "sesion_simulacion", sesion.getId(), null,
                Map.of("fechaHora", String.valueOf(datos.fechaHora()), "cupo", datos.cupo()), null);

        // Publicar una sesión mueve a quien estaba esperando fecha
        disponibilidad.recalcularPara(sesion);
        return sesion.getId();
    }

    @Override
    public List<SesionPanel> listarSesiones(ContextoUsuario quien) {
        permisos.alcanceDe("crear_sesiones_simulacion");
        List<SesionSimulacion> todas = sesiones.findByOrganizacionIdOrderByFechaHora(
                quien.organizacionId());
        if (todas.isEmpty()) {
            return List.of();
        }

        // Las cuatro cosas que cuelgan de cada sesión, para la lista entera de una vez.
        //
        // Esta lista no se filtra por fecha ni se pagina: son todas las sesiones que la
        // organización haya creado nunca, así que solo crece. Cuatro consultas por fila
        // sobre una lista que solo crece es un problema que se nota tarde, cuando ya hay
        // un año de sesiones dentro.
        List<Long> ids = todas.stream().map(SesionSimulacion::getId).toList();
        Map<Long, Long> inscritos = new HashMap<>();
        for (Object[] fila : inscripciones.contarVigentesPorSesion(ids)) {
            inscritos.put((Long) fila[0], (Long) fila[1]);
        }
        Map<Long, List<Long>> vacantesPorSesion = sesionesVacante.findBySesionSimulacionIdIn(ids)
                .stream().collect(Collectors.groupingBy(SesionVacante::getSesionSimulacionId,
                        Collectors.mapping(SesionVacante::getVacanteId, Collectors.toList())));
        Map<Long, List<Long>> responsablesPorSesion = responsables.findBySesionSimulacionIdIn(ids)
                .stream().collect(Collectors.groupingBy(SesionResponsable::getSesionSimulacionId,
                        Collectors.mapping(SesionResponsable::getUsuarioId, Collectors.toList())));
        Map<Long, List<TramoResponse>> tramosPorSesion =
                tramos.findBySesionSimulacionIdInOrderByMinutoInicio(ids).stream()
                        .collect(Collectors.groupingBy(TramoSimulacion::getSesionSimulacionId,
                                Collectors.mapping(ServicioSimulacionImpl::comoTramo,
                                        Collectors.toList())));

        return todas.stream()
                .map(s -> comoPanel(s,
                        // Una sesión sin nadie inscrito no sale de la consulta de conteo: eso
                        // es cero inscritos, no una sesión sin dato.
                        inscritos.getOrDefault(s.getId(), 0L),
                        vacantesPorSesion.getOrDefault(s.getId(), List.of()),
                        responsablesPorSesion.getOrDefault(s.getId(), List.of()),
                        tramosPorSesion.getOrDefault(s.getId(), List.of())))
                .toList();
    }

    @Override
    public SesionPanel verSesion(ContextoUsuario quien, Long sesionId) {
        return comoPanel(laSesion(quien, sesionId));
    }

    @Override
    @Transactional
    public void ampliarCupo(ContextoUsuario quien, Long sesionId, AmpliarCupo datos) {
        SesionSimulacion sesion = laSesion(quien, sesionId);
        if (datos.cupo() <= sesion.getCupo()) {
            throw new IllegalArgumentException("El cupo nuevo tiene que ser mayor que el actual");
        }
        Integer anterior = sesion.getCupo();
        sesion.setCupo(datos.cupo());
        // Si estaba llena y ahora hay sitio, vuelve a ofrecerse
        if ("LLENA".equals(sesion.getEstado())) {
            sesion.setEstado("PUBLICADA");
        }
        sesiones.save(sesion);
        auditoria.registrar(quien.organizacionId(), quien, "ampliar_cupo_sesion",
                "sesion_simulacion", sesionId, Map.of("cupo", anterior), Map.of("cupo", datos.cupo()), null);

        disponibilidad.recalcularPara(sesion);
    }

    @Override
    @Transactional
    public void cancelarSesion(ContextoUsuario quien, Long sesionId, CancelarSesion datos) {
        SesionSimulacion sesion = laSesion(quien, sesionId);
        if ("CANCELADA".equals(sesion.getEstado())) {
            throw new IllegalStateException("Esa sesión ya está cancelada");
        }
        sesion.setEstado("CANCELADA");
        sesiones.save(sesion);
        auditoria.registrar(quien.organizacionId(), quien, "cancelar_sesion_simulacion",
                "sesion_simulacion", sesionId, Map.of("estado", "PUBLICADA"),
                Map.of("estado", "CANCELADA"), datos.motivo());

        disponibilidad.alCancelarse(sesion);
    }

    @Override
    @Transactional
    public void asignarResponsable(ContextoUsuario quien, Long sesionId, AsignarResponsable datos) {
        SesionSimulacion sesion = laSesion(quien, sesionId);
        exigirRolDeFacilitador(quien.organizacionId(), datos.usuarioId());
        responsables.save(SesionResponsable.builder()
                .sesionSimulacionId(sesion.getId())
                .usuarioId(datos.usuarioId())
                .creadoEn(Instant.now())
                .build());
        auditoria.registrar(quien.organizacionId(), quien, "asignar_responsable_sesion",
                "sesion_simulacion", sesionId, null, Map.of("usuarioId", datos.usuarioId()), null);
    }

    @Override
    @Transactional
    public Long agregarInformacionCritica(ContextoUsuario quien, Long sesionId, CrearInformacionCritica datos) {
        SesionSimulacion sesion = laSesion(quien, sesionId);
        int siguiente = informacionCritica.findBySesionSimulacionIdOrderByOrden(sesionId).size() + 1;
        InformacionCritica fila = informacionCritica.save(InformacionCritica.builder()
                .sesionSimulacionId(sesion.getId())
                .tipo(datos.tipo())
                .texto(datos.texto())
                .orden(siguiente)
                .creadoEn(Instant.now())
                .build());
        return fila.getId();
    }

    @Override
    public List<InformacionCriticaResponse> verInformacionCritica(ContextoUsuario quien, Long sesionId) {
        laSesion(quien, sesionId);
        return informacionCritica.findBySesionSimulacionIdOrderByOrden(sesionId).stream()
                .map(i -> new InformacionCriticaResponse(i.getId(), i.getTipo(), i.getTexto(), i.getOrden()))
                .toList();
    }

    // ============ El candidato ============

    @Override
    public List<SesionDisponible> sesionesDisponibles(ContextoUsuario quien, UUID uuidPostulacion) {
        Postulacion postulacion = laMia(quien, uuidPostulacion);

        // Solo se ven las fechas cuando de verdad toca elegir una.
        //
        // Antes se listaban siempre, y un candidato que iba por el Perfil Integral veía la
        // sesión de simulación en su pantalla. No podía inscribirse —`inscribirse` sí mira el
        // estado y contesta «todavía no te toca»— pero eso es peor, no mejor: se le enseña
        // algo sobre lo que no puede actuar, y lo que aprende es que la pantalla miente.
        //
        // La comprobación es la misma que la de inscribirse, a propósito: si las dos no dicen
        // lo mismo, vuelve a aparecer el hueco por otro lado.
        if (!PUEDE_ELEGIR.equals(postulacion.getEstadoCodigo())) {
            return List.of();
        }

        return sesiones.disponiblesPara(postulacion.getOrganizacionId(), postulacion.getVacanteId()).stream()
                .map(s -> new SesionDisponible(s.getId(), s.getFechaHora(), s.getDuracionMinutos(),
                        s.getModalidad(), s.getLugar(), s.getEnlace(),
                        s.getCupo() - inscripciones.countBySesionSimulacionIdAndEsVigenteTrue(s.getId())))
                .toList();
    }

    @Override
    @Transactional
    public MiSesion inscribirse(ContextoUsuario quien, UUID uuidPostulacion, Long sesionId) {
        Postulacion postulacion = laMia(quien, uuidPostulacion);
        if (!PUEDE_ELEGIR.equals(postulacion.getEstadoCodigo())) {
            throw new IllegalStateException("Todavía no te toca elegir fecha para la simulación");
        }
        if (inscripciones.findByPostulacionIdAndEsVigenteTrue(postulacion.getId()).isPresent()) {
            throw new IllegalStateException("Ya elegiste una fecha. Si quieres cambiarla, avísale al equipo");
        }

        SesionSimulacion sesion = sesiones.findById(sesionId)
                .filter(s -> s.getOrganizacionId().equals(postulacion.getOrganizacionId()))
                .orElseThrow(() -> new ResourceNotFoundException("Sesión", "id", sesionId));
        boolean sirveParaSuVacante = sesionesVacante.findBySesionSimulacionId(sesionId).stream()
                .anyMatch(sv -> sv.getVacanteId().equals(postulacion.getVacanteId()));
        if (!sirveParaSuVacante || !"PUBLICADA".equals(sesion.getEstado())) {
            throw new ResourceNotFoundException("Sesión", "id", sesionId);
        }

        long ocupadas = inscripciones.countBySesionSimulacionIdAndEsVigenteTrue(sesionId);
        if (ocupadas >= sesion.getCupo()) {
            throw new IllegalStateException("Esa sesión acaba de llenarse. Elige otra fecha");
        }

        InscripcionSesion inscripcion = inscripciones.save(InscripcionSesion.builder()
                .sesionSimulacionId(sesionId)
                .postulacionId(postulacion.getId())
                .inscritaEn(Instant.now())
                .esVigente(true)
                .creadoEn(Instant.now())
                .build());

        // Si con esta se llenó, la sesión deja de ofrecerse — y quien no alcanzó plaza vuelve
        // a la bandeja del equipo como pendiente de programar.
        if (ocupadas + 1 >= sesion.getCupo()) {
            sesion.setEstado("LLENA");
            sesiones.save(sesion);
            disponibilidad.recalcularPara(sesion);
        }

        return comoMiSesion(inscripcion, sesion);
    }

    @Override
    public MiSesion miSesion(ContextoUsuario quien, UUID uuidPostulacion) {
        Postulacion postulacion = laMia(quien, uuidPostulacion);
        InscripcionSesion inscripcion = inscripciones.findByPostulacionIdAndEsVigenteTrue(postulacion.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Inscripción", "postulación", uuidPostulacion));
        SesionSimulacion sesion = sesiones.findById(inscripcion.getSesionSimulacionId()).orElseThrow();
        return comoMiSesion(inscripcion, sesion);
    }

    // ============ Durante la sesión ============

    @Override
    @Transactional
    public void marcarEvento(ContextoUsuario quien, Long inscripcionId, MarcarEvento datos) {
        InscripcionSesion inscripcion = laInscripcion(inscripcionId);
        exigirQuePuedaFacilitar(quien, inscripcion.getSesionSimulacionId());

        // Cada evento ocurre una vez. Volver a marcarlo corrige la hora, no crea otro.
        MarcaTiempoSimulacion marca = marcas
                .findByInscripcionSesionIdAndEvento(inscripcionId, datos.evento())
                .orElseGet(() -> MarcaTiempoSimulacion.builder()
                        .inscripcionSesionId(inscripcionId)
                        .evento(datos.evento())
                        .creadoEn(Instant.now())
                        .build());
        marca.setOcurridaEn(datos.ocurridaEn() == null ? Instant.now() : datos.ocurridaEn());
        marcas.save(marca);
    }

    @Override
    public List<MarcaResponse> verMarcas(ContextoUsuario quien, Long inscripcionId) {
        laInscripcion(inscripcionId);
        permisos.alcanceDe("marcar_eventos_simulacion");
        return marcas.findByInscripcionSesionIdOrderByOcurridaEn(inscripcionId).stream()
                .map(m -> new MarcaResponse(m.getEvento(), m.getOcurridaEn()))
                .toList();
    }

    @Override
    @Transactional
    public void marcarAsistencia(ContextoUsuario quien, Long inscripcionId, MarcarAsistencia datos) {
        InscripcionSesion inscripcion = laInscripcion(inscripcionId);
        permisos.alcanceDe("marcar_asistencia");

        inscripcion.setAsistio(datos.asistio());
        inscripcion.setMarcadaPorUsuarioId(quien.usuarioId());
        inscripciones.save(inscripcion);

        Postulacion postulacion = postulaciones.findById(inscripcion.getPostulacionId())
                .orElseThrow(() -> new IllegalStateException("La postulación de esta inscripción ya no existe"));

        if (Boolean.TRUE.equals(datos.asistio())) {
            if (PUEDE_ELEGIR.equals(postulacion.getEstadoCodigo())) {
                maquina.transicionar(postulacion, POR_CONFIRMAR, quien,
                        "Asistió a la sesión de simulación", false, false, null);
            }
        } else {
            // Faltar no es un estado: la inscripción guarda que no asistió y la postulación
            // vuelve a la bandeja del equipo, que decide si darle otra fecha o cerrarla.
            // NO se le reinscribe solo: eso es decisión de una persona (RF y doc 03).
            inscripcion.setEsVigente(false);
            inscripciones.save(inscripcion);
            if (PUEDE_ELEGIR.equals(postulacion.getEstadoCodigo())) {
                maquina.transicionar(postulacion, ESPERANDO, quien,
                        "No asistió a la sesión de simulación", false, false, null);
            }
        }

        auditoria.registrar(quien.organizacionId(), quien, "marcar_asistencia_simulacion",
                "inscripcion_sesion", inscripcionId, null,
                Map.of("asistio", String.valueOf(datos.asistio())), null);
    }

    @Override
    @Transactional
    public void decidirSobreAusente(ContextoUsuario quien, Long postulacionId, DecidirSobreAusente datos) {
        Postulacion postulacion = laVisible(quien, postulacionId, "decidir_sobre_ausente");
        if (!ESPERANDO.equals(postulacion.getEstadoCodigo())) {
            throw new IllegalStateException(
                    "Esta postulación no está esperando que se decida qué hacer con ella");
        }

        if ("CERRAR".equals(datos.decision())) {
            maquina.transicionar(postulacion, "NO_CONTINUA", quien, datos.motivo(),
                    false, false, "DECISION_PERSONA");
        } else {
            // Darle otra fecha: se queda esperando, y el recálculo la mueve en cuanto haya
            // una sesión con cupo. Si ya la hay, se mueve ahora mismo.
            disponibilidad.recalcularVacante(postulacion.getOrganizacionId(), postulacion.getVacanteId());
        }
        auditoria.registrar(quien.organizacionId(), quien, "decidir_sobre_ausente",
                "postulacion", postulacionId, null, Map.of("decision", datos.decision()), datos.motivo());
    }

    // ============ La conversación final ============

    @Override
    @Transactional
    public Long registrarPregunta(ContextoUsuario quien, Long postulacionId, RegistrarPregunta datos) {
        laVisible(quien, postulacionId, "hacer_conversacion_final");
        int siguiente = preguntas.findByPostulacionIdOrderByOrden(postulacionId).size() + 1;
        PreguntaGenerada fila = preguntas.save(PreguntaGenerada.builder()
                .postulacionId(postulacionId)
                .texto(datos.texto())
                .alertaId(datos.alertaId())
                .orden(siguiente)
                .creadoEn(Instant.now())
                .build());
        return fila.getId();
    }

    @Override
    @Transactional
    public void responderPregunta(ContextoUsuario quien, Long preguntaId, ResponderPregunta datos) {
        PreguntaGenerada pregunta = preguntas.findById(preguntaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pregunta", "id", preguntaId));
        laVisible(quien, pregunta.getPostulacionId(), "hacer_conversacion_final");

        pregunta.setRespuesta(datos.respuesta());
        pregunta.setRiesgoResuelto(datos.riesgoResuelto());
        pregunta.setObservacion(datos.observacion());
        pregunta.setRegistradaPorUsuarioId(quien.usuarioId());
        preguntas.save(pregunta);
    }

    @Override
    public PreguntasEncoladas generarPreguntas(ContextoUsuario quien, Long postulacionId) {
        laVisible(quien, postulacionId, "hacer_conversacion_final");
        if (!cola.encolarPreguntasSimulacion(postulacionId)) {
            return new PreguntasEncoladas("SIN_CAMBIOS",
                    "No se pidió nada: o las preguntas ya están preparadas, o hay una petición "
                            + "en marcha ahora mismo.");
        }
        return new PreguntasEncoladas("ENCOLADA",
                "Las preguntas quedaron pedidas. Tardan decenas de segundos: vuelve a consultar "
                        + "la conversación final para verlas.");
    }

    @Override
    public List<PreguntaResponse> verPreguntas(ContextoUsuario quien, Long postulacionId) {
        laVisible(quien, postulacionId, "hacer_conversacion_final");
        return preguntas.findByPostulacionIdOrderByOrden(postulacionId).stream()
                .map(p -> new PreguntaResponse(p.getId(), p.getTexto(), p.getMotivo(), p.getOrden(),
                        p.getRespuesta(), p.getRiesgoResuelto(), p.getObservacion()))
                .toList();
    }

    // ============ Apoyo ============

    /**
     * Quién puede facilitar una sesión: el permiso, más un rol de los que admite el parámetro
     * {@code roles_facilitador_simulacion}, más —si la sesión tiene responsables asignados— ser
     * uno de ellos.
     *
     * <p>El parámetro es lo que hace esto cambiable sin desplegar: hoy son Talento y Dirección,
     * mañana puede ser otro rol, y basta con editarlo desde el panel.
     */
    private void exigirQuePuedaFacilitar(ContextoUsuario quien, Long sesionId) {
        permisos.alcanceDe("marcar_eventos_simulacion");
        exigirRolDeFacilitador(quien.organizacionId(), quien.usuarioId());

        // Si la sesión tiene responsables asignados, solo ellos la conducen. Si no tiene,
        // basta con el permiso y el rol: no todas las sesiones necesitan nombrar a alguien.
        List<SesionResponsable> asignados = responsables.findBySesionSimulacionId(sesionId);
        if (!asignados.isEmpty()
                && asignados.stream().noneMatch(r -> r.getUsuarioId().equals(quien.usuarioId()))) {
            throw new AccessDeniedException(
                    "Esta sesión tiene responsables asignados y no eres uno de ellos");
        }
    }

    /**
     * Comprueba que el usuario tenga alguno de los roles que el parámetro admite.
     *
     * <p>Esto es lo que evita inventar un rol «facilitador»: se usan los roles que ya existen, y
     * cuáles valen se decide desde el panel. El valor por defecto solo entra en juego si alguien
     * borró la fila de configuración — el sistema no se cae por eso.
     */
    private void exigirRolDeFacilitador(Long organizacionId, Long usuarioId) {
        List<String> admitidos = parametros.lista(organizacionId,
                "roles_facilitador_simulacion", List.of("TALENTO", "DIRECCION"));

        List<Long> rolIds = usuarioRoles.findByUsuarioId(usuarioId).stream()
                .map(UsuarioRol::getRolId).toList();
        List<String> susRoles = roles.findAllById(rolIds).stream().map(Rol::getCodigo).toList();

        if (susRoles.stream().noneMatch(admitidos::contains)) {
            throw new AccessDeniedException(
                    "Solo pueden conducir una sesión los roles " + String.join(", ", admitidos)
                            + ". Se cambia en el parámetro roles_facilitador_simulacion.");
        }
    }

    private SesionSimulacion laSesion(ContextoUsuario quien, Long sesionId) {
        return sesiones.findByIdAndOrganizacionId(sesionId, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Sesión", "id", sesionId));
    }

    private InscripcionSesion laInscripcion(Long inscripcionId) {
        return inscripciones.findById(inscripcionId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscripción", "id", inscripcionId));
    }

    private Postulacion laMia(ContextoUsuario quien, UUID uuid) {
        return postulaciones.findByUuid(uuid)
                .filter(p -> p.getUsuarioId().equals(quien.usuarioId()))
                .orElseThrow(() -> new ResourceNotFoundException("Postulación", "código", uuid));
    }

    private Postulacion laVisible(ContextoUsuario quien, Long postulacionId, String permiso) {
        Postulacion p = postulaciones.findByIdAndOrganizacionId(postulacionId, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Postulación", "id", postulacionId));
        FiltroAlcance alcance = permisos.alcanceDe(permiso);
        if (alcance.tipo() == FiltroAlcance.Tipo.SUS_VACANTES) {
            boolean esSuya = vacantes.findById(p.getVacanteId())
                    .map(v -> quien.usuarioId().equals(v.getResponsableUsuarioId()))
                    .orElse(false);
            if (!esSuya) {
                throw new ResourceNotFoundException("Postulación", "id", postulacionId);
            }
        }
        return p;
    }

    /** Una sola sesión: se piden sus cuatro cosas sueltas porque no hay tanda con la que ir. */
    private SesionPanel comoPanel(SesionSimulacion s) {
        return comoPanel(s,
                inscripciones.countBySesionSimulacionIdAndEsVigenteTrue(s.getId()),
                sesionesVacante.findBySesionSimulacionId(s.getId()).stream()
                        .map(SesionVacante::getVacanteId).toList(),
                responsables.findBySesionSimulacionId(s.getId()).stream()
                        .map(SesionResponsable::getUsuarioId).toList(),
                tramosDe(s.getId()));
    }

    private SesionPanel comoPanel(SesionSimulacion s, long inscritos, List<Long> vacanteIds,
                                  List<Long> responsableIds, List<TramoResponse> susTramos) {
        return new SesionPanel(s.getId(), s.getFechaHora(), s.getDuracionMinutos(), s.getModalidad(),
                s.getLugar(), s.getEnlace(), s.getCupo(), inscritos,
                s.getEstado(), s.getEnunciado(), vacanteIds, responsableIds, susTramos);
    }

    private static TramoResponse comoTramo(TramoSimulacion t) {
        return new TramoResponse(t.getCodigo(), t.getNombre(), t.getMinutoInicio(), t.getMinutoFin());
    }

    private MiSesion comoMiSesion(InscripcionSesion i, SesionSimulacion s) {
        return new MiSesion(i.getId(), s.getId(), s.getFechaHora(), s.getDuracionMinutos(),
                s.getModalidad(), s.getLugar(), s.getEnlace(), s.getEnunciado(),
                i.getAsistio(), tramosDe(s.getId()));
    }

    private List<TramoResponse> tramosDe(Long sesionId) {
        return tramos.findBySesionSimulacionIdOrderByMinutoInicio(sesionId).stream()
                .map(ServicioSimulacionImpl::comoTramo)
                .toList();
    }
}
