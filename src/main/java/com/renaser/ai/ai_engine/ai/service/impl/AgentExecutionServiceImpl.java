package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.dto.AgentResponse;
import com.renaser.ai.ai_engine.ai.dto.AgentRunRequest;
import com.renaser.ai.ai_engine.ai.dto.AgentRunResponse;
import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.ai.mapper.AgentRunMapper;
import com.renaser.ai.ai_engine.ai.messaging.AgentExecutionMessage;
import com.renaser.ai.ai_engine.ai.messaging.AgentExecutionRequestPublisher;
import com.renaser.ai.ai_engine.ai.messaging.AgentHandoffPublisher;
import com.renaser.ai.ai_engine.ai.model.AgentRun;
import com.renaser.ai.ai_engine.ai.repository.AgentRunRepository;
import com.renaser.ai.ai_engine.ai.service.AgentExecutionService;
import com.renaser.ai.ai_engine.ai.service.AgentInvoker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentExecutionServiceImpl implements AgentExecutionService {

    private final AgentInvoker agentInvoker;
    private final AgentRunRepository agentRunRepository;
    private final AgentRunMapper agentRunMapper;
    private final AgentHandoffPublisher agentHandoffPublisher;
    private final AgentExecutionRequestPublisher agentExecutionRequestPublisher;

    /**
     * Sin @Transactional a propósito: agentInvoker.ask() puede tardar minutos y una transacción
     * abierta durante todo ese tiempo retiene una conexión del pool. Con el pool por defecto
     * (10 conexiones), una decena de corridas simultáneas congelaría la aplicación entera.
     * Cada save() es atómico por sí mismo, que es la única garantía que este flujo necesita.
     */
    @Override
    public AgentRunResponse execute(AgentRunRequest request) {
        UUID flowId = UUID.randomUUID();
        AgentResponse<?> aiResult = agentInvoker.ask(request);

        AgentRun run = agentRunMapper.toAgenteRun(request);
        run.setFlowId(flowId);
        run.setCreatedAt(Instant.now());
        agentInvoker.applyResult(run, aiResult);
        AgentRun saved = agentRunRepository.save(run);

        agentHandoffPublisher.publishFanOut(
                saved.getId(), flowId, saved.getEntityId(), saved.getObjective(), aiResult.routing(), 0, 1);

        return agentRunMapper.toAgenteRunResponse(saved);
    }

    @Override
    public UUID startFlow(AgentRunRequest request) {
        UUID flowId = UUID.randomUUID();
        enqueue(request, flowId, null, 0, 1);
        return flowId;
    }

    @Override
    public UUID enqueue(AgentRunRequest request) {
        return enqueue(request, UUID.randomUUID(), null, 0, 1);
    }

    @Override
    @Transactional
    public UUID enqueue(AgentRunRequest request, UUID flowId, UUID parentRunId, int depth, int totalRuns) {
        AgentRun pending = agentRunMapper.toAgenteRun(request);
        pending.setFlowId(flowId);
        pending.setParentRunId(parentRunId);
        pending.setDepth(depth);
        pending.setCreatedAt(Instant.now());
        AgentRun saved = agentRunRepository.save(pending);

        agentExecutionRequestPublisher.publishExecutionRequest(new AgentExecutionMessage(
                saved.getId(), flowId, parentRunId, request.agentType(),
                request.entityId(), request.objective(), depth, totalRuns));

        return saved.getId();
    }

    /**
     * Una corrida que revienta (modelo caído, JSON irrecuperable) debe quedar marcada como
     * fallida y visible en la traza. Sin este catch quedaba pendiente para siempre, y desde
     * fuera era indistinguible de "todavía procesando".
     *
     * Tampoco lleva @Transactional, por el mismo motivo que execute(): la llamada al modelo
     * nunca debe ocurrir con una transacción abierta.
     */
    @Override
    public void completeExecution(AgentExecutionMessage message) {
        AgentRun run = agentRunRepository.findById(message.runId())
                .orElseThrow(() -> new ResourceNotFoundException("AgentRun", "id", message.runId()));

        AgentRunRequest request = new AgentRunRequest(
                message.agentType(), message.entityId(), message.objective());

        AgentResponse<?> aiResult;
        try {
            aiResult = agentInvoker.ask(request);
        } catch (RuntimeException e) {
            log.error("La corrida {} ({}) falló: {}", message.runId(), message.agentType(), e.getMessage());
            run.setErrorMessage(e.getClass().getSimpleName() + ": " + e.getMessage());
            run.setFinishedAt(Instant.now());
            agentRunRepository.save(run);
            return;
        }

        agentInvoker.applyResult(run, aiResult);
        AgentRun saved = agentRunRepository.save(run);

        agentHandoffPublisher.publishFanOut(saved.getId(), message.flowId(), saved.getEntityId(),
                saved.getObjective(), aiResult.routing(), message.depth(), message.totalRuns());
    }
}
