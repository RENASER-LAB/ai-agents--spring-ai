package com.renaser.ai.ai_engine.ai.dto;

import com.renaser.ai.ai_engine.ai.model.AgentType;
public record AgentRunRequest(
        AgentType agentType,
        String entityId,
        String objective
) {
}
