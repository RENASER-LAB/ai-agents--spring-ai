package com.renaser.ai.ai_engine.ai.dto.payload;

// Orchestrator no hace análisis de negocio propio — su salida real es el campo
// routing[] del envelope compartido. Este payload queda vacío a propósito.
public record OrchestratorPayload() {
}
