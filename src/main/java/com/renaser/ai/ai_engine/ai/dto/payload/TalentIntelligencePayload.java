package com.renaser.ai.ai_engine.ai.dto.payload;

import java.util.List;

public record TalentIntelligencePayload(
        HeadcountGate headcountGate,
        List<Candidate> candidates,
        String qohFeedback
) {
    public record HeadcountGate(String decision, double realGap) { // not_needed|open_position
    }

    public record Candidate(String candidateId, double evidenceScore, double challengeScore,
                             List<String> risks, List<String> missingEvidence) {
    }
}
