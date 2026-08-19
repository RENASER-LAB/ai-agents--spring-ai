package com.renaser.ai.ai_engine.ai.dto;

import java.util.List;

// Contrato común de los 15 agentes (sección 3.1 del manual V2), genérico sobre el
// payload específico de cada agente. Un solo record: nada que repetir por agente.
public record AgentResponse<P>(
        Severity severity,
        List<FactItem> facts,
        List<MissingDataItem> missingData,
        ConfidenceBlock confidence,
        HumanGateBlock humanGate,
        List<NextActionItem> nextActions,
        List<RoutingItem> routing,
        P payload
) {
}
