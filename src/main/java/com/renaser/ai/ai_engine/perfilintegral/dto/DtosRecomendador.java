package com.renaser.ai.ai_engine.perfilintegral.dto;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.DatosDeLaVacante;

import java.math.BigDecimal;
import java.util.List;

/**
 * El ida y vuelta del RECOMENDADOR (V66): lo que se le enseña para proponer criterios y
 * preguntas propias, y lo que devuelve.
 *
 * <p>Un solo juego de records, como en {@link DtosCalificacionIa}: los que se le enseñan al
 * modelo son los que se leen de su respuesta, y los que el panel pinta como propuesta.
 */
public final class DtosRecomendador {

    private DtosRecomendador() {
    }

    /**
     * Lo que recibe: los datos de la vacante (el mismo conjunto que el evaluador), cuántos
     * puntos tiene que sumar su propuesta, la indicación de quien la pidió y lo que ya hay en
     * el borrador, con sus puntos, para no repetirlo.
     */
    public record InsumoRecomendador(DatosDeLaVacante vacante, int puntosQueFaltan,
                                     String indicacion, List<String> tiposPermitidos,
                                     List<CriterioDelBorrador> criteriosDelBorrador,
                                     List<PreguntaDelBorrador> preguntasSinCriterio) {
    }

    /** Un criterio que ya está en el borrador. Su id es el que la IA usa para llenarlo. */
    public record CriterioDelBorrador(Long id, String nombre, String queEvalua, int puntos,
                                      List<PreguntaDelBorrador> preguntas) {
    }

    public record PreguntaDelBorrador(String tipo, String enunciado, int puntos) {
    }

    /** Lo que devuelve: criterios, nuevos o del borrador, cada uno con sus preguntas. */
    public record ResultadoRecomendador(List<CriterioPropuesto> criterios) {
    }

    /**
     * Un criterio propuesto. Con {@code criterioExistenteId} son preguntas para un criterio
     * que ya está en el borrador (y el nombre se ignora); sin él, es un criterio nuevo.
     */
    public record CriterioPropuesto(Long criterioExistenteId, String nombre, String queEvalua,
                                    List<PreguntaPropuesta> preguntas,
                                    /* Solo en la prueba técnica (V67): los puntos de su parte
                                       calificada, quién la califica (IA o PERSONA) y qué
                                       entregables mira, por su posición en la propuesta
                                       (`entregables`) o por su id si ya están en el borrador
                                       (`entregablesExistentes`). */
                                    BigDecimal parteCalificada, String calificador,
                                    List<Integer> entregables, List<Long> entregablesExistentes) {

        public CriterioPropuesto(Long criterioExistenteId, String nombre, String queEvalua,
                                 List<PreguntaPropuesta> preguntas) {
            this(criterioExistenteId, nombre, queEvalua, preguntas, null, null, null, null);
        }
    }

    /** El caso que propone la IA para la prueba técnica (V67). */
    public record CasoPropuesto(String enunciado, String materiales, String herramientasPermitidas) {
    }

    /**
     * Un entregable que propone la IA (V67). {@code detalle} es qué debe contener;
     * {@code queDebeTener}, qué distingue una buena entrega.
     */
    public record EntregablePropuesto(String nombre, String detalle, String formato,
                                      Boolean obligatorio, String queDebeTener) {
    }

    /**
     * Lo que devuelve el RECOMENDADOR para una prueba técnica (V67): el caso (si el borrador
     * no tiene), los entregables nuevos y los criterios con su parte calificada.
     */
    public record ResultadoRecomendadorPrueba(CasoPropuesto caso,
                                              List<EntregablePropuesto> entregables,
                                              List<CriterioPropuesto> criterios) {
    }

    /** Lo que se guarda de una propuesta de prueba, ya validada. */
    public record PropuestaDePrueba(CasoPropuesto caso, List<EntregablePropuesto> entregables,
                                    List<CriterioPropuesto> criterios) {
    }

    /**
     * Lo que recibe para proponer una prueba técnica (V67): lo de siempre más el caso, el
     * tiempo y los entregables que ya hay, y la parte calificada de cada criterio.
     */
    public record InsumoRecomendadorPrueba(DatosDeLaVacante vacante, int puntosQueFaltan,
                                           String indicacion, List<String> tiposPermitidos,
                                           String enunciadoActual, String modalidad,
                                           Integer duracionMinutos, Integer plazoDias,
                                           List<EntregableDelBorrador> entregablesDelBorrador,
                                           List<CriterioDelBorradorDePrueba> criteriosDelBorrador) {
    }

    public record EntregableDelBorrador(Long id, String nombre, String formato) {
    }

    public record CriterioDelBorradorDePrueba(Long id, String nombre, String queEvalua,
                                              int puntosDeCerradas, int parteCalificada,
                                              String calificador, List<Long> entregables,
                                              List<PreguntaDelBorrador> preguntas) {
    }

    /** Una pregunta propuesta. Los puntos llegan como número para poder rechazar decimales. */
    public record PreguntaPropuesta(String tipo, String enunciado, BigDecimal puntos,
                                    String queDebeTener, List<OpcionPropuesta> opciones) {
    }

    /** Una opción o un nivel. En la escala, el texto es el rótulo y puede ir vacío. */
    public record OpcionPropuesta(String texto, BigDecimal puntos) {
    }
}
