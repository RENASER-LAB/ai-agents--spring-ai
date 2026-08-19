package com.renaser.ai.ai_engine.ai.service;

import com.renaser.ai.ai_engine.ai.dto.AgentResponse;
import com.renaser.ai.ai_engine.ai.dto.AgentRunRequest;
import com.renaser.ai.ai_engine.ai.model.AgentRun;

/**
 * Frontera entre la orquestación del flujo y la conversación con el modelo.
 * <p>
 * AgentExecutionService depende de este contrato y no de cómo se arma el prompt, se eligen
 * las opciones del proveedor o se parsea el envelope. Eso permite ejercitar el flujo
 * completo — guardar, encolar, publicar handoff — con un doble de prueba, sin levantar un
 * ChatClient ni gastar saldo de la API.
 */
public interface AgentInvoker {

    /**
     * Ejecuta el agente contra el modelo y devuelve el envelope tipado.
     *
     * @throws IllegalStateException si el modelo no devuelve un envelope parseable dentro
     *                               del número de intentos configurado.
     */
    AgentResponse<?> ask(AgentRunRequest request);

    /** Vuelca el resultado del agente sobre la corrida que se va a persistir. */
    void applyResult(AgentRun run, AgentResponse<?> aiResult);
}
