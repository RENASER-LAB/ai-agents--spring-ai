package com.renaser.ai.ai_engine.ai.prompt.impl;

import com.renaser.ai.ai_engine.ai.model.AgentType;
import com.renaser.ai.ai_engine.ai.prompt.AgentPromptProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

@Component
public class AgentPromptProviderImpl implements AgentPromptProvider {

    private static final String BASE_PROMPT_PATH = "prompts/base-system-prompt.md";
    private static final String PROMPTS_DIR = "prompts/";

    private final String basePrompt;
    private final Map<AgentType, String> promptsByAgent = new EnumMap<>(AgentType.class);

    // Carga todo al arrancar la app: si falta un archivo de prompt, falla acá
    // y no en medio de una ejecución real de agente.
    public AgentPromptProviderImpl() {
        this.basePrompt = readResource(BASE_PROMPT_PATH);
        for (AgentType agentType : AgentType.values()) {
            String fileName = PROMPTS_DIR + agentType.name().toLowerCase() + ".md";
            promptsByAgent.put(agentType, readResource(fileName));
        }
    }

    @Override
    public String getSystemPrompt(AgentType agentType) {
        return basePrompt + System.lineSeparator() + promptsByAgent.get(agentType);
    }

    private String readResource(String path) {
        Resource resource = new PathMatchingResourcePatternResolver().getResource("classpath:" + path);
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo cargar el prompt: " + path, e);
        }
    }
}
