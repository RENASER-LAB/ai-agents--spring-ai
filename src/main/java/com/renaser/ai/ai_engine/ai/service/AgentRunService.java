package com.renaser.ai.ai_engine.ai.service;

import com.renaser.ai.ai_engine.ai.dto.AgentRunRequest;
import com.renaser.ai.ai_engine.ai.dto.AgentRunResponse;
import com.renaser.ai.ai_engine.ai.model.AgentType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentRunService {

    // Registra un nuevo análisis a partir de lo que pide el request (createdAt se setea acá)
    AgentRunResponse registerRun(AgentRunRequest request);

    // Busca un run puntual por su id (útil para consultar el estado de una ejecución encolada)
    Optional<AgentRunResponse> getById(UUID id);

    // Historial completo de una entidad (cliente/empleado/cohorte), más reciente primero
    List<AgentRunResponse> getHistory(String entityId);

    // Todo lo que ha analizado un agente en particular
    List<AgentRunResponse> getRunsByAgent(AgentType agentType);

    // Bandeja de pendientes por aprobar (Human Gate)
    List<AgentRunResponse> getPendingApprovals();

    // Último resultado de un agente sobre una entidad, si existe
    Optional<AgentRunResponse> getLastRun(String entityId, AgentType agentType);

    // Aprueba un run pendiente de Human Gate. Lanza NoSuchElementException si no existe.
    AgentRunResponse approve(UUID runId);
}
