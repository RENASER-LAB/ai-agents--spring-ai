package com.renaser.ai.ai_engine.ai.service;

import com.renaser.ai.ai_engine.ai.dto.AgentRunRequest;
import com.renaser.ai.ai_engine.ai.dto.AgentRunResponse;
import com.renaser.ai.ai_engine.ai.messaging.AgentExecutionMessage;

import java.util.UUID;

public interface AgentExecutionService {

    // Ejecuta un agente contra el modelo de IA de forma síncrona: el llamador espera la respuesta completa
    AgentRunResponse execute(AgentRunRequest request);

    // Arranca un flujo nuevo y devuelve su flowId, con el que se consulta la traza paso a paso
    UUID startFlow(AgentRunRequest request);

    // Encola un caso nuevo (depth=0, totalRuns=1) y devuelve de inmediato el id del run (pendiente)
    UUID enqueue(AgentRunRequest request);

    // Encola la continuación de una cadena existente (handoff), conservando el flujo y el padre
    UUID enqueue(AgentRunRequest request, UUID flowId, UUID parentRunId, int depth, int totalRuns);

    // Llamado por el listener de la cola: ejecuta el agente y completa el run ya creado por enqueue()
    void completeExecution(AgentExecutionMessage message);
}
