package com.renaser.ai.ai_engine.ai.messaging;

import com.renaser.ai.ai_engine.ai.dto.AgentRunRequest;
import com.renaser.ai.ai_engine.ai.service.AgentExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.renaser.ai.ai_engine.ai.config.RabbitMQConfig.AGENT_HANDOFF_QUEUE;

@Component
@RequiredArgsConstructor
@Slf4j
public class AgentHandoffListener {

    private final AgentExecutionService agentExecutionService;

    @RabbitListener(queues = AGENT_HANDOFF_QUEUE)
    public void onHandoff(AgentHandoffMessage message) {
        log.info("Handoff recibido: run origen={}, flow={}, entidad={}, agente siguiente={}, depth={}",
                message.sourceRunId(), message.flowId(), message.entityId(),
                message.nextAgent(), message.depth());

        AgentRunRequest nextRequest = new AgentRunRequest(message.nextAgent(), message.entityId(), message.objective());
        agentExecutionService.enqueue(nextRequest, message.flowId(), message.sourceRunId(),
                message.depth(), message.totalRuns());
    }
}
