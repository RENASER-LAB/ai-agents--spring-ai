package com.renaser.ai.ai_engine.simulacion.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * El contrato entre el motor de agentes y la conversación final de la simulación.
 *
 * <p>La conversación final son quince minutos al cierre de la sesión en los que alguien le
 * pregunta al candidato por lo que acaba de hacer. Su valor entero está en <b>qué</b> se le
 * pregunta: una pregunta genérica —«cuéntame de una vez que fallaste»— no aporta nada que no
 * estuviera ya en el currículum. Una que nombra un hecho de esa misma mañana, sí.
 *
 * <p>Por eso el insumo es un montón de cosas pequeñas y no un texto: notas, hallazgos,
 * alertas, afirmaciones del currículum y las horas de la sesión. La contradicción vive
 * <b>entre</b> dos de esas cosas, y para verla hay que tener las dos delante.
 *
 * <p><b>Este agente no puntúa nada</b>, así que puede leer datos que los que califican no
 * ven: lo que produce es una pregunta para una persona, no un número que entre en una nota.
 */
public final class DtosSimulacionIa {

    private DtosSimulacionIa() {
    }

    /** Lo que se le enseña al agente que prepara las preguntas. */
    public record InsumoConversacion(
            String puesto,
            String nivelPuesto,
            String queBuscaLaVacante,
            /* El retrato del Perfil de Talento, en las palabras con que se guardó */
            String retrato,
            List<LoQueSeVio> hallazgos,
            List<Contradiccion> alertas,
            List<DijoEnElCurriculum> afirmacionesDelCurriculum,
            List<NotaDeLaSimulacion> notasDeLaSimulacion,
            List<MomentoDeLaSesion> lineaDeTiempo,
            List<LoQueEscribio> respuestasDeLaPrueba) {
    }

    /** tipo: FORTALEZA · RIESGO_CRITICO · RIESGO_DESARROLLABLE · PREFERENCIA · FALTA_EVIDENCIA. */
    public record LoQueSeVio(String tipo, String descripcion, String evidencia) {
    }

    /**
     * Una alerta ya levantada, con su id.
     *
     * <p>El id viaja de ida y de vuelta: si la pregunta sale de esta alerta, el agente lo
     * devuelve y la pregunta queda atada a ella. Es lo que permite después ver si el riesgo
     * que la alerta señalaba quedó resuelto en la conversación o sigue abierto.
     */
    public record Contradiccion(Long alertaId, String tipo, String descripcion) {
    }

    /** clasificacion: DEMOSTRADA · DECLARADA · CONTRADICHA · FALTA_INFO. */
    public record DijoEnElCurriculum(String texto, String clasificacion) {
    }

    /** Una nota de los diez criterios de la simulación, con lo que el facilitador escribió. */
    public record NotaDeLaSimulacion(String criterio, BigDecimal puntaje, BigDecimal puntosMaximos,
                                     String explicacion) {
    }

    /**
     * Un momento observable de la sesión, con su hora.
     *
     * <p>Son solo actos que alguien vio (RF-98): nunca lo que se supone que el candidato
     * pensó. De la distancia entre dos de estas horas salen las mejores preguntas, y por eso
     * viajan como texto de reloj y no como marca de tiempo: el modelo tiene que poder
     * escribirlas dentro de la pregunta tal cual.
     */
    public record MomentoDeLaSesion(String evento, String hora, Integer minutoDeLaSesion) {
    }

    /** Lo que escribió en la prueba del puesto, que es de donde salen las contradicciones. */
    public record LoQueEscribio(String pregunta, String respuesta) {
    }

    /** Lo que devuelve. */
    public record ResultadoConversacion(List<PreguntaIa> preguntas) {
    }

    /**
     * Una pregunta lista para leerse en voz alta.
     *
     * @param motivo   el hecho concreto del que sale. No es adorno: es lo que permite al
     *                 facilitador repreguntar cuando la respuesta se va por las ramas
     * @param alertaId la alerta de la que sale, o vacío si no sale de ninguna
     */
    public record PreguntaIa(String texto, String motivo, Long alertaId) {
    }
}
