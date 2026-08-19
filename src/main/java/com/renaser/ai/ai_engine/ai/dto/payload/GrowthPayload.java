package com.renaser.ai.ai_engine.ai.dto.payload;

import java.util.List;

public record GrowthPayload(
        List<FunnelStage> funnel,
        String dominantBottleneck,
        String economicGap,
        Experiment experiment
) {
    public record FunnelStage(String stage, double current, double target, double gap) {
    }

    public record Experiment(String hypothesis, String metric, String successCriterion, double budget) {
    }
}
