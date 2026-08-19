package com.renaser.ai.ai_engine.ai.messaging;

import com.renaser.ai.ai_engine.ai.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AgentExecutionRequestPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishExecutionRequest(AgentExecutionMessage message) {
        log.info("Encolando ejecución: run={}, agente={}, entidad={}",
                message.runId(), message.agentType(), message.entityId());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.AGENT_EXCHANGE, RabbitMQConfig.AGENT_EXECUTION_ROUTING_KEY, message);
    }
}
