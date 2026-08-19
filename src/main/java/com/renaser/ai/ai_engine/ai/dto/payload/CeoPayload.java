package com.renaser.ai.ai_engine.ai.dto.payload;

import java.util.List;

public record CeoPayload(
        List<Decision> decisions,
        List<Risk> risks,
        List<Opportunity> opportunities
) {
    public record Decision(String title, String impact, String doNothingConsequence,
                            List<String> options, String recommendation, double confidence, String deadline) {
    }

    public record Risk(String title, double probability, String impact, String window) {
    }

    public record Opportunity(String title, String value, String nextMove) {
    }
}
