package com.renaser.ai.ai_engine.ai.dto;

// Escala 0.0-1.0 (no 0-100 — este es el contrato V2, distinto al borrador anterior)
public record ConfidenceBlock(double overall, double dataCompleteness, double evidenceStrength) {
}
