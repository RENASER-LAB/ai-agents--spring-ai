package com.renaser.ai.ai_engine.ai.service;

import com.renaser.ai.ai_engine.ai.dto.RespuestaModelo;

/**
 * Una llamada al modelo, y nada más.
 *
 * <p><b>Por qué es una interfaz de un solo método.</b> Todo lo demás —la bitácora, los
 * reintentos, la cola, qué se guarda— se puede probar sin gastar un centavo si esta pieza se
 * puede sustituir por un doble. Sin ella, cualquier prueba del camino completo tendría que
 * llamar de verdad a DeepSeek, y eso significa pruebas lentas, caras y distintas cada vez.
 *
 * <p>También es lo que permite mover el chat a un modelo local sin tocar a los tres agentes:
 * hoy el candidato se lee en un servicio externo y esa decisión sigue abierta.
 */
public interface ClienteModelo {

    /**
     * @param agenteCodigo cuál de los agentes pregunta; permite elegir modelo por agente
     * @param instruccion  el texto que Dirección administra desde el panel
     * @param contenido    los datos del candidato, ya en JSON
     */
    RespuestaModelo preguntar(String agenteCodigo, String instruccion, String contenido);

    /**
     * Lo mismo, pero eligiendo si el modelo razona antes de contestar.
     *
     * <p><b>Es la palanca de velocidad del sistema.</b> Razonando, el modelo escribe unos
     * 6.400 tokens por currículum y solo 1.300 son la respuesta: los otros 5.100 son
     * pensamiento interno que nunca se guarda, y que se paga en segundos. Sin razonar la
     * misma llamada baja de 48 a 19 segundos.
     *
     * <p>No sale gratis. Medido sobre los mismos diez currículums, sin razonar solo tres
     * quedan en la misma posición y el modelo detecta menos riesgos críticos. Sirve para
     * ordenar una tanda entera; no para decidir a quién se contrata.
     */
    RespuestaModelo preguntar(String agenteCodigo, String instruccion, String contenido,
                              boolean razona);
}
