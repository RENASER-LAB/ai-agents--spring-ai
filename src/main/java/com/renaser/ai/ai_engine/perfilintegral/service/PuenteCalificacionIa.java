package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.InsumoCv;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.InsumoDatos;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoDatos;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.InsumoPerfil;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.InsumoPorPuntos;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.InsumoRespuestas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoCv;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoEvaluador;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoPerfil;

/**
 * La única puerta entre el motor de agentes y la selección de personal.
 *
 * <p><b>Por qué existe.</b> Los tres agentes de calificación viven bajo {@code ai/} y las
 * tablas que escriben —{@code nota_criterio}, {@code nota_respuesta}, {@code perfil_talento},
 * {@code alerta}, {@code nota_etapa}— son del módulo de selección, que mantiene otra persona.
 * Si cada agente usara los repositorios de selección directamente, la frontera entre los dos
 * módulos desaparecería y cualquier cambio de un lado rompería el otro sin avisar.
 *
 * <p>Con esta interfaz el motor de agentes solo sabe dos verbos: <b>pedir los datos</b> de un
 * candidato y <b>entregar el resultado</b>. Todo lo demás —qué tabla, qué versión de pesos,
 * qué estado sigue— se decide de este lado.
 *
 * <p><b>Reglas que se cumplen aquí y no en el agente</b>, porque son del negocio y no del
 * modelo:
 * <ul>
 *   <li>Una nota sin explicación no se guarda (RF-150).
 *   <li>La nota queda atada a la versión de pesos de la vacante, nunca a la última publicada.
 *   <li>La postulación solo se mueve con {@code MaquinaEstados}, nunca a mano.
 * </ul>
 */
public interface PuenteCalificacionIa {

    /** La organización de una postulación: la necesita cada fila de {@code trabajo_ia}. */
    Long organizacionDe(Long postulacionId);

    /**
     * Si esta postulación tiene una evaluación entregada de la que salgan respuestas.
     *
     * <p>Lo pregunta la cola para saber si el evaluador tiene trabajo. Sin evaluación —una
     * criba en la que solo se leyó el currículum— llamarlo gastaría una petición al modelo
     * para no puntuar nada, así que la fila se lo salta y pasa directo al Perfil de Talento.
     */
    boolean tieneEvaluacionEntregada(Long postulacionId);

    /**
     * Si esta postulación ya tiene la ficha de datos sacada del currículum.
     *
     * <p>Lo pregunta la cola para no volver a pagarla. Son datos copiados —teléfono, último
     * puesto, años de experiencia—, no una nota: una vez sacados no cambian salvo que el
     * currículum cambie, y en ese caso quien lo reemplaza borra la ficha.
     */
    boolean tieneFichaCv(Long postulacionId);

    // ==================== DATOS_CV ====================

    /** El currículum recortado, para el agente que solo saca datos y no puntúa. */
    InsumoDatos insumoDatos(Long postulacionId);

    /**
     * Guarda la ficha de datos del candidato.
     *
     * <p>La rehace entera cada vez. No hay ajuste a mano que respetar aquí: son datos
     * copiados del currículum, no notas.
     */
    void guardarDatos(Long postulacionId, Long ejecucionIaId, ResultadoDatos resultado);

    // ==================== EVIDENCIA_CV ====================

    /**
     * El currículum ya recortado más los ocho criterios con su peso para este nivel.
     *
     * @throws IllegalStateException si no se pudo obtener el texto del currículum. No se
     *                               devuelve un insumo a medias: sin currículum no hay nota.
     */
    InsumoCv insumoCv(Long postulacionId);

    /** Guarda las ocho notas del currículum y las afirmaciones clasificadas. */
    void guardarEvidenciaCv(Long postulacionId, Long ejecucionIaId, ResultadoCv resultado);

    // ==================== EVALUADOR ====================

    /** Las respuestas abiertas que puntúan. Puede venir vacío: no todo el mundo tiene. */
    InsumoRespuestas insumoRespuestas(Long postulacionId);

    void guardarNotasAbiertas(Long postulacionId, Long ejecucionIaId, ResultadoEvaluador resultado);

    // ---------- Las preguntas propias de la vacante (método PUNTOS, V66) ----------

    /**
     * Lo que el evaluador necesita para calificar las abiertas por puntos: las que tienen
     * puntos y algo escrito, sin las ajustadas a mano (esas no se tocan), con su máximo, su
     * criterio y los datos de la vacante.
     *
     * @return nulo si la evaluación de esa postulación no es de este método: el evaluador
     *         sigue entonces por su camino de siempre
     */
    InsumoPorPuntos insumoPorPuntos(Long postulacionId);

    /**
     * Guarda las notas de las abiertas, con la red de seguridad del backend: descarta
     * respuestas que no son de esta evaluación y notas sin explicación, acota cada nota entre
     * 0 y el máximo de su pregunta, y nunca guarda una nota global.
     *
     * @param versionGuia    con qué guía se calcularon (la del insumo)
     * @param recalificacion si viene de corregir la guía: entonces las notas de la persona se
     *                       aplican juntas o ninguna, y al terminar se recalculan sus notas
     *                       sin moverla de etapa
     * @return false si la guía cambió mientras se calificaba: no se guardó nada y hay que
     *         volver a pedirlo con la nueva
     */
    boolean guardarNotasPorPuntos(Long postulacionId, Long ejecucionIaId,
                                  ResultadoEvaluador resultado, int versionGuia,
                                  boolean recalificacion);

    /**
     * Recalcula la nota del Perfil Integral y el grupo de prioridad <b>sin llamar a la IA y
     * sin mover a nadie de etapa</b>: el camino del ajuste a mano, del cambio de puntos y de
     * la recalificación. El grupo se decide con los hallazgos ya guardados del retrato.
     */
    void recalcularSinMover(Long postulacionId);

    // ---------- El cuestionario técnico de la vacante (etapa 2) ----------
    // Lo mismo contra el otro examen de la postulación. El método es siempre CRITERIOS y la
    // guía de cada pregunta viaja igual, porque vive en la propia `pregunta`.

    InsumoRespuestas insumoRespuestasTecnicas(Long postulacionId);

    void guardarNotasTecnicas(Long postulacionId, Long ejecucionIaId, ResultadoEvaluador resultado);

    /**
     * Cerrar la nota del cuestionario técnico sin pasar por el modelo.
     *
     * <p>Para quien entregó sin escribir nada: no hay ninguna respuesta que calificar, pero sí
     * hay nota que poner — todo ceros. Sin esto, esa postulación se quedaría en
     * PRUEBA_CALIFICANDO para siempre.
     */
    void cerrarNotaTecnica(Long postulacionId);

    // ==================== POTENCIAL_RIESGO ====================

    /** Todo lo ya calificado, que es de donde sale el Perfil de Talento. */
    InsumoPerfil insumoPerfil(Long postulacionId);

    /**
     * Cierra la etapa: guarda el Perfil de Talento con sus hallazgos, recalcula la nota del
     * Perfil Integral, asigna el grupo de prioridad y mueve la postulación a
     * {@code PERFIL_POR_CONFIRMAR}, que es donde una persona decide.
     */
    void cerrarPerfilIntegral(Long postulacionId, Long ejecucionIaId, ResultadoPerfil resultado);
}
