package com.renaser.ai.ai_engine.ai.flow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Traza completa de un flujo: qué agentes actuaron, en qué orden, quién disparó a quién
// y qué devolvió cada uno.
public record FlowTraceResponse(
        UUID flowId,
        String entityId,
        String objective,
        FlowStatus status,
        int totalSteps,
        int completedSteps,
        Instant startedAt,
        Long totalDurationMs,
        List<FlowStepResponse> steps
) {
}
