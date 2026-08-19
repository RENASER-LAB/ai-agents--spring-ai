package com.renaser.ai.ai_engine.ai.dto.payload;

import java.util.List;

public record OperationsPayload(
        List<Bottleneck> bottlenecks,
        Rework rework,
        String capacityState,
        List<String> optimizationOptions
) {
    public record Bottleneck(String processStep, String delay, String impact, List<String> evidenceIds) {
    }

    public record Rework(double hours) {
    }
}
