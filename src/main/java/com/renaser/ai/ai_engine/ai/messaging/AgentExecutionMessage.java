package com.renaser.ai.ai_engine.ai.messaging;

import com.renaser.ai.ai_engine.ai.model.AgentType;

import java.util.UUID;

// Lo que viaja por RabbitMQ para pedirle al listener que ejecute un agente de forma asíncrona.
// runId ya existe en la base (creado por enqueue()) — el listener completa esa fila, no crea una nueva.
// flowId/parentRunId reconstruyen el árbol del flujo; depth/totalRuns permiten a
// AgentChainLimits cortar la cadena.
public record AgentExecutionMessage(
        UUID runId,
        UUID flowId,
        UUID parentRunId,
        AgentType agentType,
        String entityId,
        String objective,
        int depth,
        int totalRuns
) {
}
