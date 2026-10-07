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
                                       calificada y quién la califica (IA o PERSONA).
                                       `entregables` y `entregablesExistentes` eran lo que miraba
                                       (V67); desde la V68 la IA nunca propone «Mira»: se deduce
                                       del alcance de los entregables. Se conservan solo para
                                       leer las propuestas guardadas antes. */
                                    BigDecimal parteCalificada, String calificador,
                                    List<Integer> entregables, List<Long> entregablesExistentes,
                                    /* Solo en la prueba técnica (V69): lo que vale el criterio
                                       nuevo entero. Su parte calificada es esto menos sus
                                       cerradas. Nulo en las propuestas guardadas antes, que
                                       solo traen la parte calificada. */
                                    BigDecimal puntos) {

        public CriterioPropuesto(Long criterioExistenteId, String nombre, String queEvalua,
                                 List<PreguntaPropuesta> preguntas) {
            this(criterioExistenteId, nombre, queEvalua, preguntas, null, null, null, null, null);
        }

        /** Lo que suman sus cerradas propuestas (las abiertas no llevan puntos). */
        public int puntosDeCerradas() {
            return preguntas == null ? 0 : preguntas.stream()
                    .filter(p -> p != null && p.tipo() != null && !"ABIERTA".equals(p.tipo())
                            && p.puntos() != null)
                    .mapToInt(p -> p.puntos().intValue())
                    .sum();
        }

        /**
         * Lo que vale este criterio nuevo entero: lo propuesto o, en una propuesta de antes de
         * la V69, sus cerradas más su parte calificada. Nulo si no dice ni una cosa ni otra.
         */
        public BigDecimal puntosDelCriterio() {
            if (puntos != null) {
                return puntos;
            }
            return parteCalificada == null ? null
                    : parteCalificada.add(BigDecimal.valueOf(puntosDeCerradas()));
        }
    }

    /** El caso que propone la IA para la prueba técnica (V67). */
    public record CasoPropuesto(String enunciado, String materiales, String herramientasPermitidas) {
    }

    /**
     * Un entregable que propone la IA (V67). {@code detalle} es qué debe contener;
     * {@code queDebeTener}, qué distingue una buena entrega.
     *
     * <p>Su alcance (V68): {@code pregunta} para el archivo de una pregunta de la propuesta;
     * si no, es general y cubre {@code todaLaPrueba} o las preguntas de la propuesta de
     * {@code cubre}. Sin nada de eso (las propuestas de antes), cubre toda la prueba.
     */
    public record EntregablePropuesto(String nombre, String detalle, String formato,
                                      Boolean obligatorio, String queDebeTener,
                                      PosicionDePregunta pregunta, Boolean todaLaPrueba,
                                      List<PosicionDePregunta> cubre) {

        public EntregablePropuesto(String nombre, String detalle, String formato,
                                   Boolean obligatorio, String queDebeTener) {
            this(nombre, detalle, formato, obligatorio, queDebeTener, null, true, List.of());
        }

        /** El archivo de una pregunta de la propuesta. */
        public boolean esDeUnaPregunta() {
            return pregunta != null;
        }

        /** Un general que cubre toda la prueba: lo dice, o no dice nada (las de antes). */
        public boolean cubreTodaLaPrueba() {
            return pregunta == null && (Boolean.TRUE.equals(todaLaPrueba)
                    || (todaLaPrueba == null && (cubre == null || cubre.isEmpty())));
        }
    }

    /** Una pregunta de la propuesta: la posición de su criterio y la suya, desde 0. */
    public record PosicionDePregunta(Integer criterio, Integer pregunta) {
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

    /**
     * Un entregable que ya está en el borrador. {@code todaLaPrueba} (V68): si es general de
     * toda la prueba, también lo mirarán los criterios nuevos.
     */
    public record EntregableDelBorrador(Long id, String nombre, String formato, boolean todaLaPrueba) {

        public EntregableDelBorrador(Long id, String nombre, String formato) {
            this(id, nombre, formato, false);
        }
    }

    /**
     * Un criterio que ya está en el borrador. {@code puntos} (V69) es lo que vale y no cambia
     * aunque se le agreguen preguntas: sus cerradas nuevas salen de su parte calificada.
     */
    public record CriterioDelBorradorDePrueba(Long id, String nombre, String queEvalua,
                                              int puntosDeCerradas, int parteCalificada,
                                              String calificador, List<Long> entregables,
                                              List<PreguntaDelBorrador> preguntas, int puntos) {

        public CriterioDelBorradorDePrueba(Long id, String nombre, String queEvalua,
                                           int puntosDeCerradas, int parteCalificada,
                                           String calificador, List<Long> entregables,
                                           List<PreguntaDelBorrador> preguntas) {
            this(id, nombre, queEvalua, puntosDeCerradas, parteCalificada, calificador, entregables,
                    preguntas, puntosDeCerradas + parteCalificada);
        }
    }

    /** Una pregunta propuesta. Los puntos llegan como número para poder rechazar decimales. */
    public record PreguntaPropuesta(String tipo, String enunciado, BigDecimal puntos,
                                    String queDebeTener, List<OpcionPropuesta> opciones) {
    }

    /** Una opción o un nivel. En la escala, el texto es el rótulo y puede ir vacío. */
    public record OpcionPropuesta(String texto, BigDecimal puntos) {
    }
}
