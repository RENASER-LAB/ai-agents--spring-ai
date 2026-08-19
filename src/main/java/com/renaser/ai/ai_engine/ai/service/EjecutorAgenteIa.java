package com.renaser.ai.ai_engine.ai.service;

import com.renaser.ai.ai_engine.ai.model.TrabajoIa;

/**
 * Una llamada al modelo, con su bitácora.
 *
 * <p>Los tres agentes de calificación no hablan con el modelo directamente: pasan por aquí.
 * Así <b>toda</b> llamada queda escrita en {@code ejecucion_ia} —con qué agente, qué versión,
 * qué instrucción, qué se envió, qué respondió, cuánto tardó y cuánto costó— salga bien o
 * salga mal. Es requisito de auditoría (RF-146), y si dependiera de que cada agente se
 * acuerde de anotarlo, tarde o temprano uno no lo haría.
 */
public interface EjecutorAgenteIa {

    /**
     * Pregunta al modelo y devuelve la respuesta ya leída.
     *
     * @param trabajo   la fila de {@code trabajo_ia} que se está atendiendo
     * @param objetivo  en una frase, qué se le pidió; queda en la bitácora
     * @param formato   el JSON exacto que se espera de vuelta, para pegar a la instrucción
     * @param insumo    los datos del candidato; se serializan a JSON
     * @param tipo      a qué record se lee la respuesta
     * @throws IllegalStateException si el agente no tiene instrucción activa, si el modelo
     *                               falla o si lo que devuelve no se puede leer. En los tres
     *                               casos queda la fila de {@code ejecucion_ia} con el error,
     *                               y el trabajo se reintenta: <b>nunca se inventa una nota</b>.
     */
    <T> Ejecutado<T> ejecutar(TrabajoIa trabajo, String objetivo, String formato,
                              Object insumo, Class<T> tipo);

    /**
     * Lo mismo, eligiendo si el modelo razona antes de contestar.
     *
     * <p>Razonar es lo que hace que una llamada tarde 48 segundos en vez de 19. Los agentes
     * que puntúan lo deciden por el modo de su trabajo; el que solo extrae datos nunca
     * razona, porque copiar un dato de un texto no tiene nada que deliberar.
     */
    <T> Ejecutado<T> ejecutar(TrabajoIa trabajo, String objetivo, String formato,
                              Object insumo, Class<T> tipo, boolean razona);

    /** El resultado leído, y el id de la fila de bitácora con que hay que sellarlo. */
    record Ejecutado<T>(Long ejecucionIaId, T resultado) {
    }
}
