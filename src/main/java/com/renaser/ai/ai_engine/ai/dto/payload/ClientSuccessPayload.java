package com.renaser.ai.ai_engine.ai.dto.payload;

import java.util.List;

public record ClientSuccessPayload(
        double healthScore,
        List<HealthDriver> healthDrivers,
        String riskReason,
        String nextBestAction,
        String recoveryCheckAt
) {
    public record HealthDriver(String component, double score, String direction) {
    }
}
