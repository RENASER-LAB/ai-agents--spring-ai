package com.renaser.ai.ai_engine.ai.dto;

import java.util.List;

// Un hecho con su evidencia — nunca un hecho suelto sin respaldo
public record FactItem(String text, List<String> evidenceIds) {
}
