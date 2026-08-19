package com.renaser.ai.ai_engine.ai.dto.payload;

import java.util.List;

public record EventPayload(
        String mode, // plan|readiness|live|post
        List<String> readinessDomains,
        List<String> blockingConditions,
        List<String> liveIncidents,
        PostPipeline postPipeline
) {
    public record PostPipeline(int openOpportunities, int withoutNextAction) {
    }
}
