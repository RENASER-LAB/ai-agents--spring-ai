package com.renaser.ai.ai_engine.ai.dto.payload;

import java.util.List;

public record KnowledgePayload(
        List<Source> sources,
        String answer,
        List<String> conflicts,
        String changeProposal
) {
    public record Source(String documentId, String version, String section, String support) {
    }
}
