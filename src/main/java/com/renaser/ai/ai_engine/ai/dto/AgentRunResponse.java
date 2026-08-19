package com.renaser.ai.ai_engine.ai.dto;

import com.renaser.ai.ai_engine.ai.model.AgentType;

import java.time.Instant;
import java.util.UUID;

public record AgentRunResponse(
        UUID id,
        AgentType agentType,
        String entityId,
        String objective,
        String outputJson,
        String severity,
        boolean requiresHumanApproval,
        boolean approved,
        Instant createdAt,
        String version
) {
}
