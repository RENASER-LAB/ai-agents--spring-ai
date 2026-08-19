package com.renaser.ai.ai_engine.ai.dto;

// Human Gate por acción, no por agente completo: leer/analizar puede ser autónomo,
// contratar/renegociar/pagar no.
public record HumanGateBlock(boolean required, String action, String reason, String approverRoleId) {
}
