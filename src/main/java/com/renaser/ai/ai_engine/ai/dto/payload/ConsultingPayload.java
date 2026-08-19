package com.renaser.ai.ai_engine.ai.dto.payload;

import java.util.List;

public record ConsultingPayload(
        List<String> businessHealth,
        List<String> topBottlenecks,
        List<String> interventions,
        List<String> impactLedgerEntries
) {
}
