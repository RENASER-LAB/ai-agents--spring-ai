package com.renaser.ai.ai_engine.ai.dto.payload;

import java.util.List;

public record NarrativeMessagePayload(
        String message,
        List<String> channelConstraints,
        List<String> variables,
        List<String> sensitiveClaims
) {
}
