package com.renaser.ai.ai_engine.ai.messaging;

import com.renaser.ai.ai_engine.ai.config.RabbitMQConfig;
import com.renaser.ai.ai_engine.ai.dto.RoutingItem;
import com.renaser.ai.ai_engine.ai.model.AgentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AgentHandoffPublisher {

    private final RabbitTemplate rabbitTemplate;

    // Publica un handoff por cada entrada de routing[] (fan-out real), respetando
    // AgentChainLimits. Corta toda la rama si el siguiente salto excede max_depth;
    // corta destinos individuales si exceden max_agent_runs.
    public void publishFanOut(UUID runId, UUID flowId, String entityId, String objective,
                               List<RoutingItem> routing, int depth, int totalRuns) {
        if (routing == null || routing.isEmpty()) {
            return;
        }

        int nextDepth = depth + 1;
        if (nextDepth > AgentChainLimits.MAX_DEPTH) {
            log.warn("Cadena cortada por profundidad máxima ({}): run={}, entidad={}",
                    AgentChainLimits.MAX_DEPTH, runId, entityId);
            return;
        }

        int runsSoFar = totalRuns;
        List<RoutingItem> byPriority = routing.stream()
                .sorted(Comparator.comparingInt(RoutingItem::priority))
                .toList();

        for (RoutingItem item : byPriority) {
            if (runsSoFar >= AgentChainLimits.MAX_AGENT_RUNS) {
                log.warn("Cadena cortada por máximo de runs ({}): run={}, entidad={}, destino omitido={}",
                        AgentChainLimits.MAX_AGENT_RUNS, runId, entityId, item.agentId());
                break;
            }

            AgentType target = resolveAgentType(item.agentId());
            if (target == null) {
                log.warn("Destino de routing no reconocido, se ignora: run={}, agentId crudo={}", runId, item.agentId());
                continue;
            }

            runsSoFar++;
            publish(new AgentHandoffMessage(runId, flowId, entityId, objective, target, nextDepth, runsSoFar));
        }
    }

    private void publish(AgentHandoffMessage message) {
        String routingKey = "agent.handoff." + message.nextAgent().name().toLowerCase();

        log.info("Publicando handoff: run={}, flow={}, entidad={}, siguiente agente={}, depth={}, totalRuns={}",
                message.sourceRunId(), message.flowId(), message.entityId(),
                message.nextAgent(), message.depth(), message.totalRuns());

        rabbitTemplate.convertAndSend(RabbitMQConfig.AGENT_EXCHANGE, routingKey, message);
    }

    // Mismo criterio defensivo que ya usábamos con nextAgent: el modelo a veces manda
    // "AG-06", a veces "FINANCE", a veces "Finance Agent" — nunca debe romper la cadena.
    private AgentType resolveAgentType(String rawAgentId) {
        if (rawAgentId == null || rawAgentId.isBlank()) {
            return null;
        }
        String normalized = rawAgentId.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        for (AgentType type : AgentType.values()) {
            if (normalized.equals(type.name()) || normalized.endsWith("_" + type.name())) {
                return type;
            }
        }
        return null;
    }
}
