package com.renaser.ai.ai_engine.ai.repository;

import com.renaser.ai.ai_engine.ai.model.AgentRun;
import com.renaser.ai.ai_engine.ai.model.AgentType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentRunRepository extends JpaRepository<AgentRun, UUID> {

    //muestra todo lo que se ha analizado
    List<AgentRun> findByEntityIdOrderByCreatedAtDesc(String entityId);
    //muestra todo lo que el agente a analizado una vez
    List<AgentRun> findByAgentType(AgentType agentType);
    //muestra lo que la aprobacion humano
    List<AgentRun> findByRequiresHumanApprovalTrueAndApprovedFalse();
    //analiza si el resultado de una agente si otro lo hizo
    Optional<AgentRun> findTopByEntityIdAndAgentTypeOrderByCreatedAtDesc(String entityId, AgentType agentType);
    //todas las corridas de un mismo flujo, en el orden en que se dispararon
    List<AgentRun> findByFlowIdOrderByCreatedAtAsc(UUID flowId);
}
