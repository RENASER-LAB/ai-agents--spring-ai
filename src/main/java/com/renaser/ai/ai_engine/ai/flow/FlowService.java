package com.renaser.ai.ai_engine.ai.flow;

import java.util.Optional;
import java.util.UUID;

public interface FlowService {

    // Traza paso a paso de un flujo. Empty si el flowId no existe.
    Optional<FlowTraceResponse> getTrace(UUID flowId);
}
