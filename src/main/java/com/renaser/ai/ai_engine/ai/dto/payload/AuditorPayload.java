package com.renaser.ai.ai_engine.ai.dto.payload;

import java.util.List;

public record AuditorPayload(
        String auditScope,
        String standardVersion,
        List<Criterion> criteria,
        boolean systemicPattern,
        boolean decisionRequired
) {
    public record Criterion(String criterion, String status, List<String> evidenceIds) {
    }
}
