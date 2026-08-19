package com.renaser.ai.ai_engine.ai.dto;

// Un dato que falta, por qué hace falta, y si bloquea la conclusión
public record MissingDataItem(String field, String whyNeeded, boolean blocking) {
}
