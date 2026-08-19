package com.renaser.ai.ai_engine.ai.dto;

import com.renaser.ai.ai_engine.ai.dto.payload.*;
import com.renaser.ai.ai_engine.ai.model.AgentType;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.Map;

// Une cada AgentType con el ParameterizedTypeReference exacto de su AgentResponse<Payload>.
// Necesario porque .entity() de Spring AI necesita el tipo genérico completo en tiempo de
// compilación (borrado de tipos de Java) — no hay forma de construirlo dinámicamente.
@Component
public class AgentResponseTypeRegistry {

    private final Map<AgentType, ParameterizedTypeReference<?>> types = Map.ofEntries(
            Map.entry(AgentType.ORCHESTRATOR, new ParameterizedTypeReference<AgentResponse<OrchestratorPayload>>() {
            }),
            Map.entry(AgentType.CEO, new ParameterizedTypeReference<AgentResponse<CeoPayload>>() {
            }),
            Map.entry(AgentType.AUDITOR, new ParameterizedTypeReference<AgentResponse<AuditorPayload>>() {
            }),
            Map.entry(AgentType.DIAGNOSTIC, new ParameterizedTypeReference<AgentResponse<DiagnosticPayload>>() {
            }),
            Map.entry(AgentType.OPERATIONS, new ParameterizedTypeReference<AgentResponse<OperationsPayload>>() {
            }),
            Map.entry(AgentType.CLIENT_SUCCESS, new ParameterizedTypeReference<AgentResponse<ClientSuccessPayload>>() {
            }),
            Map.entry(AgentType.FINANCE, new ParameterizedTypeReference<AgentResponse<FinancePayload>>() {
            }),
            Map.entry(AgentType.COLLECTIONS, new ParameterizedTypeReference<AgentResponse<CollectionsPayload>>() {
            }),
            Map.entry(AgentType.TALENT_INTELLIGENCE, new ParameterizedTypeReference<AgentResponse<TalentIntelligencePayload>>() {
            }),
            Map.entry(AgentType.GROWTH, new ParameterizedTypeReference<AgentResponse<GrowthPayload>>() {
            }),
            Map.entry(AgentType.EVENT, new ParameterizedTypeReference<AgentResponse<EventPayload>>() {
            }),
            Map.entry(AgentType.CONSULTING, new ParameterizedTypeReference<AgentResponse<ConsultingPayload>>() {
            }),
            Map.entry(AgentType.KNOWLEDGE, new ParameterizedTypeReference<AgentResponse<KnowledgePayload>>() {
            }),
            Map.entry(AgentType.QA_GOVERNANCE, new ParameterizedTypeReference<AgentResponse<QaGovernancePayload>>() {
            }),
            Map.entry(AgentType.NARRATIVE_MESSAGE, new ParameterizedTypeReference<AgentResponse<NarrativeMessagePayload>>() {
            })
    );

    public ParameterizedTypeReference<?> resolve(AgentType agentType) {
        ParameterizedTypeReference<?> type = types.get(agentType);
        if (type == null) {
            throw new IllegalArgumentException("No hay tipo de respuesta registrado para " + agentType);
        }
        return type;
    }
}
