package com.renaser.ai.ai_engine.ai.messaging;

import com.renaser.ai.ai_engine.ai.service.AgentExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.renaser.ai.ai_engine.ai.config.RabbitMQConfig.AGENT_EXECUTION_QUEUE;

@Component
@RequiredArgsConstructor
@Slf4j
public class AgentExecutionRequestListener {

    private final AgentExecutionService agentExecutionService;

    @RabbitListener(queues = AGENT_EXECUTION_QUEUE)
    public void onExecutionRequest(AgentExecutionMessage message) {
        log.info("Ejecutando de forma asíncrona: run={}, agente={}", message.runId(), message.agentType());
        agentExecutionService.completeExecution(message);
    }
}
