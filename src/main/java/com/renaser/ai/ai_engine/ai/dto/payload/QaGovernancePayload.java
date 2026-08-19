package com.renaser.ai.ai_engine.ai.dto.payload;

import java.util.List;

public record QaGovernancePayload(
        boolean pass,
        List<Violation> violations,
        String recommendedGovernanceAction
) {
    // type: schema|evidence|freshness|permission|policy|autonomy|security
    public record Violation(String type, String detail, String severity) {
    }
}
