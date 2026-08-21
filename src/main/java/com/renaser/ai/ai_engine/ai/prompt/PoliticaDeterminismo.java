package com.renaser.ai.ai_engine.ai.prompt;

/**
 * Cuánta libertad se le da al modelo, agente por agente.
 *
 * <p><b>Por qué existe.</b> Hasta ahora nadie fijaba la temperatura en ningún sitio del
 * proyecto, así que todas las llamadas salían con el valor por defecto del proveedor, que no
 * es cero. El mismo currículum, calificado dos veces, dio 71,00 y 58,50. Parte de esa
 * distancia se explica porque eran modelos distintos —la pasada rápida y la fina—, pero la
 * temperatura suelta la agranda y, sobre todo, la vuelve imposible de explicar.
 *
 * <p><b>Por qué importa aquí más que en otro proyecto.</b> Esto decide a quién se contrata.
 * Un candidato puede reclamar su nota, y la única respuesta aceptable es volver a pasar el
 * mismo currículum por el mismo agente y enseñar que sale lo mismo. Con la temperatura por
 * defecto eso no se puede prometer.
 *
 * <p><b>La regla, en una frase:</b> quien pone un número va a temperatura cero; quien escribe
 * texto para que lo lea una persona, no.
 *
 * @see com.renaser.ai.ai_engine.ai.prompt.impl.PoliticaDeterminismoImpl para el detalle de
 *      hasta dónde llega esto y dónde deja de llegar
 */
public interface PoliticaDeterminismo {

    /**
     * La temperatura que le toca a un agente.
     *
     * @param codigoAgente el código del agente de selección ({@code DATOS_CV},
     *                     {@code EVIDENCIA_CV}, {@code EVALUADOR}, {@code POTENCIAL_RIESGO},
     *                     {@code PRUEBA_PUESTO}, {@code SIMULACION}) o el nombre de un
     *                     {@code AgentType} del motor. Se admiten los dos porque hay dos
     *                     caminos de llamada y la política tiene que ser la misma en ambos.
     */
    double temperaturaDe(String codigoAgente);

    /**
     * ¿Este agente escribe texto para que lo lea una persona, en vez de poner una nota?
     *
     * <p>Es la única razón por la que un agente se libra de la temperatura cero.
     */
    boolean redactaParaUnaPersona(String codigoAgente);

    /**
     * La semilla fija de los agentes que puntúan.
     *
     * <p><b>Ojo:</b> hoy no llega al proveedor. Ver el javadoc de
     * {@link com.renaser.ai.ai_engine.ai.prompt.impl.PoliticaDeterminismoImpl}, apartado
     * «La semilla todavía no viaja».
     */
    Integer semilla();
}
