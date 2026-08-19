package com.renaser.ai.ai_engine.ai.model;

// V2 (producción) — reemplaza el borrador de 15 agentes genéricos.
// Growth/Ads del borrador original salen del alcance local; en su lugar entran
// los agentes reales del negocio RENASER según el documento de producción.
public enum AgentType
{
    ORCHESTRATOR,        // AG-00
    CEO,                 // AG-01
    AUDITOR,              // AG-02
    DIAGNOSTIC,           // AG-03
    OPERATIONS,           // AG-04 (antes PROCESS)
    CLIENT_SUCCESS,       // AG-05 (antes CLIENT)
    FINANCE,              // AG-06
    COLLECTIONS,          // AG-07 (nuevo)
    TALENT_INTELLIGENCE,  // AG-08 (antes TALENT)
    GROWTH,               // AG-09 (nuevo)
    EVENT,                // AG-10 (nuevo)
    CONSULTING,           // AG-11 (nuevo)
    KNOWLEDGE,            // AG-12
    QA_GOVERNANCE,        // AG-13 (antes QA)
    NARRATIVE_MESSAGE     // AG-14 (nuevo)
}
