package com.renaser.ai.ai_engine.ai.rag;

import java.util.Map;

// Un fragmento recuperado del vector store, sin exponer la clase Document de Spring AI hacia afuera
public record SearchResultResponse(String id, String text, Map<String, Object> metadata) {
}
