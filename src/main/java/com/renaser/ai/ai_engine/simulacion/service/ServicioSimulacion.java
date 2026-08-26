package com.renaser.ai.ai_engine.simulacion.service;

import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacion.*;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import java.util.List;
import java.util.UUID;

/**
 * La simulación de trabajo: sesiones, inscripciones, lo que ocurre durante la sesión y la
 * conversación final.
 *
 * <p>A diferencia del resto del sistema —donde el candidato actúa solo y cuando quiere—, esta
 * etapa pasa en un momento programado y con alguien mirando. Eso explica la forma de casi todo
 * lo que hay aquí: cupos, inscripciones que se pueden perder, y un facilitador que anota
 * eventos mientras ocurren.
 */
public interface ServicioSimulacion {

    // ---------- Administración de sesiones ----------

    Long crearSesion(ContextoUsuario quien, CrearSesion datos);
    List<SesionPanel> listarSesiones(ContextoUsuario quien);
    SesionPanel verSesion(ContextoUsuario quien, Long sesionId);

    /**
     * Quién eligió esta fecha, con el nombre y la inscripción de cada uno.
     *
     * <p>Aparte de {@code verSesion} y no dentro de {@link SesionPanel} porque el panel lista
     * todas las sesiones que la organización creó nunca: resolver los nombres de todas para
     * enseñar una sería pagar la lista entera en cada carga.
     *
     * <p>Lo que devuelve depende del alcance que el rol tenga en
     * {@code ver_inscritos_simulacion}, y ese alcance se cambia desde el panel.
     */
    List<InscritoEnSesion> listarInscritos(ContextoUsuario quien, Long sesionId);

    void ampliarCupo(ContextoUsuario quien, Long sesionId, AmpliarCupo datos);
    void cancelarSesion(ContextoUsuario quien, Long sesionId, CancelarSesion datos);
    void asignarResponsable(ContextoUsuario quien, Long sesionId, AsignarResponsable datos);

    Long agregarInformacionCritica(ContextoUsuario quien, Long sesionId, CrearInformacionCritica datos);
    List<InformacionCriticaResponse> verInformacionCritica(ContextoUsuario quien, Long sesionId);

    // ---------- El candidato ----------

    List<SesionDisponible> sesionesDisponibles(ContextoUsuario quien, UUID uuidPostulacion);
    MiSesion inscribirse(ContextoUsuario quien, UUID uuidPostulacion, Long sesionId);
    MiSesion miSesion(ContextoUsuario quien, UUID uuidPostulacion);

    // ---------- Durante la sesión ----------

    /**
     * Marca uno de los diez eventos observables.
     *
     * <p>Solo puede hacerlo quien tenga el permiso <b>y</b> un rol de los que el parámetro
     * {@code roles_facilitador_simulacion} admite — así se puede cambiar quién facilita sin
     * tocar código.
     */
    void marcarEvento(ContextoUsuario quien, Long inscripcionId, MarcarEvento datos);
    List<MarcaResponse> verMarcas(ContextoUsuario quien, Long inscripcionId);

    void marcarAsistencia(ContextoUsuario quien, Long inscripcionId, MarcarAsistencia datos);

    /** Qué hacer con quien faltó: darle otra fecha o cerrar su postulación. Nunca automático. */
    void decidirSobreAusente(ContextoUsuario quien, Long postulacionId, DecidirSobreAusente datos);

    // ---------- La conversación final ----------

    /**
     * Pide que la IA prepare las preguntas, en vez de escribirlas a mano.
     *
     * <p>Las saca de lo que el candidato mostró antes: el retrato, las alertas, las notas de
     * la simulación y las horas de la sesión. Tarda decenas de segundos, así que no contesta
     * con las preguntas: contesta que quedó pedido, y se consultan con {@link #verPreguntas}.
     *
     * <p>Se puede pedir más de una vez. <b>Las que ya se hicieron y se contestaron no se
     * tocan</b>: lo que se dijo en la sala es un hecho ocurrido, y una segunda pasada no
     * puede hacerlo desaparecer.
     */
    PreguntasEncoladas generarPreguntas(ContextoUsuario quien, Long postulacionId);

    Long registrarPregunta(ContextoUsuario quien, Long postulacionId, RegistrarPregunta datos);
    void responderPregunta(ContextoUsuario quien, Long preguntaId, ResponderPregunta datos);
    List<PreguntaResponse> verPreguntas(ContextoUsuario quien, Long postulacionId);
}
