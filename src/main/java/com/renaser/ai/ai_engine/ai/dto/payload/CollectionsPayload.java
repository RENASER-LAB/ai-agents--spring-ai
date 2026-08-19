package com.renaser.ai.ai_engine.ai.dto.payload;

public record CollectionsPayload(
        String collectionState,
        double amountDue,
        String promiseToPay,
        String messageAction,
        String nextFollowUp,
        String escalation
) {
}
