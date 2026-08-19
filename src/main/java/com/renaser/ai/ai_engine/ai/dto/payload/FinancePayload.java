package com.renaser.ai.ai_engine.ai.dto.payload;

import java.util.List;

public record FinancePayload(
        Cash cash,
        List<String> forecast,
        List<String> budgetVariances,
        RevenueLeakage revenueLeakage,
        List<String> materialRisks
) {
    public record Cash(double current, String currency, String asOf) {
    }

    public record RevenueLeakage(double total, List<String> items) {
    }
}
