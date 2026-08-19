package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.context.AgentContextResolver;
import com.renaser.ai.ai_engine.ai.dto.AgentResponse;
import com.renaser.ai.ai_engine.ai.dto.AgentResponseTypeRegistry;
import com.renaser.ai.ai_engine.ai.dto.AgentRunRequest;
import com.renaser.ai.ai_engine.ai.model.AgentRun;
import com.renaser.ai.ai_engine.ai.prompt.AgentPromptProvider;
import com.renaser.ai.ai_engine.ai.prompt.ChatOptionsFactory;
import com.renaser.ai.ai_engine.ai.service.AgentInvoker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

/**
 * Conversación con DeepSeek: arma el prompt, resuelve el contexto, pide el envelope tipado
 * y lo vuelca sobre la corrida.
 */
@Component
@Slf4j
public class AgentInvokerImpl implements AgentInvoker {

    private static final String CONTRACT_VERSION = "v2";

    private final ChatClient chatClient;
    private final AgentPromptProvider agentPromptProvider;
    private final ChatOptionsFactory chatOptionsFactory;
    private final AgentResponseTypeRegistry agentResponseTypeRegistry;
    private final AgentContextResolver agentContextResolver;
    private final JsonMapper jsonMapper;
    private final int maxIntentos;

    public AgentInvokerImpl(ChatClient chatClient,
                            AgentPromptProvider agentPromptProvider,
                            ChatOptionsFactory chatOptionsFactory,
                            AgentResponseTypeRegistry agentResponseTypeRegistry,
                            AgentContextResolver agentContextResolver,
                            JsonMapper jsonMapper,
                            @Value("${renaser.ai.chat.max-intentos}") int maxIntentos) {
        this.chatClient = chatClient;
        this.agentPromptProvider = agentPromptProvider;
        this.chatOptionsFactory = chatOptionsFactory;
        this.agentResponseTypeRegistry = agentResponseTypeRegistry;
        this.agentContextResolver = agentContextResolver;
        this.jsonMapper = jsonMapper;
        this.maxIntentos = maxIntentos;
    }

    /**
     * Reintenta ante fallo de parseo porque sin structured output nativo el contrato JSON no
     * está garantizado por el proveedor. En la corrida end-to-end del 2026-08-16, 6 de 18
     * agentes fallaron por dos motivos, ambos transitorios y ambos resueltos por reintento:
     * <p>
     * - Respuesta vacía (4 casos). DeepSeek documenta que ocasionalmente devuelve content
     *   vacío; Jackson lo reporta como "No content to map due to end-of-input".
     * - Envelope truncado (2 casos). Se corrigió subiendo max-tokens, pero el reintento queda
     *   como red de seguridad si un contexto grande vuelve a acercarse al techo.
     * <p>
     * No hay backoff a propósito: ninguno de los dos fallos es por saturación del proveedor,
     * así que esperar no aporta nada y solo alarga una corrida que ya tarda decenas de segundos.
     */
    @Override
    public AgentResponse<?> ask(AgentRunRequest request) {
        String systemPrompt = agentPromptProvider.getSystemPrompt(request.agentType());
        String userMessage = agentContextResolver.buildUserMessage(request);
        ParameterizedTypeReference<?> responseType = agentResponseTypeRegistry.resolve(request.agentType());

        JacksonException ultimoFallo = null;
        for (int intento = 1; intento <= maxIntentos; intento++) {
            try {
                return (AgentResponse<?>) chatClient.prompt()
                        .system(systemPrompt)
                        .user(userMessage)
                        .options(chatOptionsFactory.forAgent(request.agentType()))
                        .call()
                        .entity(responseType);
            } catch (JacksonException e) {
                ultimoFallo = e;
                log.warn("Intento {}/{} de {} devolvió un envelope no parseable: {}",
                        intento, maxIntentos, request.agentType(), e.getMessage());
            }
        }

        throw new IllegalStateException(
                "El agente %s no devolvió un envelope válido en %d intentos"
                        .formatted(request.agentType(), maxIntentos), ultimoFallo);
    }

    @Override
    public void applyResult(AgentRun run, AgentResponse<?> aiResult) {
        run.setVersion(CONTRACT_VERSION);
        run.setOutputJson(writeJson(aiResult));
        run.setSeverity(aiResult.severity() != null ? aiResult.severity().name() : null);
        run.setRequiresHumanApproval(aiResult.humanGate() != null && aiResult.humanGate().required());
        run.setFinishedAt(Instant.now());
    }

    private String writeJson(AgentResponse<?> aiResult) {
        // Jackson 3: JacksonException ya es unchecked, este catch solo agrega un mensaje de dominio
        try {
            return jsonMapper.writeValueAsString(aiResult);
        } catch (JacksonException e) {
            throw new IllegalStateException("No se pudo serializar la respuesta del agente", e);
        }
    }
}
