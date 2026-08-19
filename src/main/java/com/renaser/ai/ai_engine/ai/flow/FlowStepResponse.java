package com.renaser.ai.ai_engine.ai.flow;

import com.renaser.ai.ai_engine.ai.model.AgentType;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

// Un paso del flujo, ya resumido: lo esencial de la corrida sin obligar a leer el JSON crudo.
public record FlowStepResponse(
        int step,
        UUID runId,
        UUID parentRunId,
        int depth,
        AgentType agentType,
        FlowStatus status,
        String severity,
        boolean requiresHumanApproval,
        Long durationMs,
        List<String> facts,
        List<String> missingData,
        List<String> routedTo,
        String errorMessage,
        JsonNode payload
) {
}
