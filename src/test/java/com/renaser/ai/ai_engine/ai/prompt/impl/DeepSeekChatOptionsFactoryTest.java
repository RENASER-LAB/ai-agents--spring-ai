package com.renaser.ai.ai_engine.ai.prompt.impl;

import com.renaser.ai.ai_engine.ai.model.AgentType;
import com.renaser.ai.ai_engine.ai.prompt.AgentModelSelector;
import org.junit.jupiter.api.Test;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.ResponseFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Corre sin levantar Spring ni tocar la base: ese es el punto de haber sacado la
 * construcción de opciones fuera de AgentInvoker.
 */
class DeepSeekChatOptionsFactoryTest {

    private static final int MAX_TOKENS = 4096;

    private DeepSeekChatOptions optionsFor(AgentType agentType, AgentModelSelector selector) {
        return (DeepSeekChatOptions) new DeepSeekChatOptionsFactory(selector, MAX_TOKENS)
                .forAgent(agentType)
                .build();
    }

    @Test
    void fuerzaJsonModeEnTodosLosAgentes() {
        // DeepSeek no tiene JSON Schema estricto: si el response_format no sale como
        // json_object, el envelope llega como prosa y el converter no tiene nada que parsear.
        AgentModelSelector selector = agentType -> "deepseek-v4-pro";

        for (AgentType agentType : AgentType.values()) {
            assertThat(optionsFor(agentType, selector).getResponseFormat().getType())
                    .as("responseFormat de %s", agentType)
                    .isEqualTo(ResponseFormat.Type.JSON_OBJECT);
        }
    }

    @Test
    void aplicaElTechoDeTokensParaQueElEnvelopeNoSalgaTruncado() {
        AgentModelSelector selector = agentType -> "deepseek-v4-pro";

        assertThat(optionsFor(AgentType.CEO, selector).getMaxTokens()).isEqualTo(MAX_TOKENS);
    }

    @Test
    void respetaElModeloQueResuelveElSelectorPorAgente() {
        // El override por agente es el mecanismo que mantiene a ORCHESTRATOR en
        // deepseek-v4-pro. Si la factory ignorara al selector, poblar MODEL_OVERRIDES
        // no tendría ningún efecto y el ahorro sería silenciosamente cero.
        AgentModelSelector selector = agentType ->
                agentType == AgentType.NARRATIVE_MESSAGE ? "deepseek-v4-flash" : "deepseek-v4-pro";

        assertThat(optionsFor(AgentType.NARRATIVE_MESSAGE, selector).getModel())
                .isEqualTo("deepseek-v4-flash");
        assertThat(optionsFor(AgentType.ORCHESTRATOR, selector).getModel())
                .isEqualTo("deepseek-v4-pro");
    }
}
