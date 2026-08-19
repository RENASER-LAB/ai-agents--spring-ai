package com.renaser.ai.ai_engine.ai.dto.payload;

import java.util.List;

public record DiagnosticPayload(
        Symptom symptom,
        List<Hypothesis> hypotheses,
        TestToConfirm testToConfirm,
        String causeStatus // unknown|probable|confirmed
) {
    public record Symptom(String metric, double target, double current, double gap) {
    }

    public record Hypothesis(String text, List<String> supporting, List<String> contradicting,
                              double confidence, String status) {
    }

    public record TestToConfirm(String action, String successCriterion) {
    }
}
