package com.renaser.ai.ai_engine.ai.dto;

import java.util.List;

// agentId llega como texto crudo del modelo (ej. "AG-06" o "FINANCE") — se interpreta
// de forma defensiva al procesar el handoff, igual que hacíamos con nextAgent antes.
public record RoutingItem(String agentId, String reason, int priority, List<String> dependsOn) {
}
