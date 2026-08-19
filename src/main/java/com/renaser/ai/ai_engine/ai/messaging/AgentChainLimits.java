package com.renaser.ai.ai_engine.ai.messaging;

// Límites del Runtime Context del manual V2 (sección 3): sin esto, un routing en círculo
// (A -> B -> A) quema GPU indefinidamente. Un caso fresco arranca en depth=0, totalRuns=1.
public final class AgentChainLimits {

    public static final int MAX_DEPTH = 4;
    public static final int MAX_AGENT_RUNS = 6;

    private AgentChainLimits() {
    }
}
