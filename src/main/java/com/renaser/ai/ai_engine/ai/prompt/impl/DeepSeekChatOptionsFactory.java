package com.renaser.ai.ai_engine.ai.prompt.impl;

import com.renaser.ai.ai_engine.ai.model.AgentType;
import com.renaser.ai.ai_engine.ai.prompt.AgentModelSelector;
import com.renaser.ai.ai_engine.ai.prompt.ChatOptionsFactory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Opciones de chat para DeepSeek.
 * <p>
 * Dos decisiones que conviene no perder de vista:
 * <p>
 * 1. responseFormat es JSON_OBJECT, no JSON Schema. DeepSeek no soporta schema estricto, así
 * que el schema del envelope viaja en el prompt (lo inyecta el converter de Spring AI) y el
 * modelo solo garantiza que la salida sea JSON sintácticamente válido, no que respete el
 * contrato. Por eso base-system-prompt.md incluye la palabra "json" y un ejemplo: son
 * requisito documentado del JSON mode de DeepSeek, no adorno.
 * <p>
 * 2. maxTokens es explícito. Un envelope truncado a mitad de camino es JSON inválido, y ese
 * fue exactamente el modo de falla que descartó a qwen3:0.6b con su ventana de 4096.
 */
@Component
public class DeepSeekChatOptionsFactory implements ChatOptionsFactory {

    private static final ResponseFormat JSON_OBJECT = ResponseFormat.builder()
            .type(ResponseFormat.Type.JSON_OBJECT)
            .build();

    private final AgentModelSelector agentModelSelector;
    private final Integer maxTokens;

    public DeepSeekChatOptionsFactory(AgentModelSelector agentModelSelector,
                                      @Value("${renaser.ai.chat.max-tokens}") Integer maxTokens) {
        this.agentModelSelector = agentModelSelector;
        this.maxTokens = maxTokens;
    }

    @Override
    public ChatOptions.Builder<?> forAgent(AgentType agentType) {
        return DeepSeekChatOptions.builder()
                .responseFormat(JSON_OBJECT)
                .model(agentModelSelector.selectModel(agentType))
                .maxTokens(maxTokens);
    }
}
