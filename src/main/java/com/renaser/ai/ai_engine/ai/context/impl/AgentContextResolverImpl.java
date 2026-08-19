package com.renaser.ai.ai_engine.ai.context.impl;

import com.renaser.ai.ai_engine.ai.context.AgentContextProvider;
import com.renaser.ai.ai_engine.ai.context.AgentContextResolver;
import com.renaser.ai.ai_engine.ai.dto.AgentRunRequest;
import com.renaser.ai.ai_engine.ai.model.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Arma el mensaje de usuario final: objective + contexto externo del agente (si tiene).
 *
 * Concentra aquí las tres garantías que antes no existían y que rompen en cuanto los datos
 * crecen: presupuesto de tokens, degradación elegante ante fallo de la fuente, y trazabilidad
 * de qué se le inyectó al modelo.
 */
@Component
@Slf4j
public class AgentContextResolverImpl implements AgentContextResolver {

    private final Map<AgentType, AgentContextProvider> providersByAgent;
    private final int maxContextChars;

    public AgentContextResolverImpl(List<AgentContextProvider> providers,
                                    @Value("${renaser.context.max-chars:12000}") int maxContextChars) {
        this.providersByAgent = providers.stream()
                .collect(Collectors.toMap(AgentContextProvider::agentType, Function.identity()));
        this.maxContextChars = maxContextChars;

        log.info("Agentes con contexto externo conectado: {}", providersByAgent.keySet());
    }

    @Override
    public String buildUserMessage(AgentRunRequest request) {
        AgentContextProvider provider = providersByAgent.get(request.agentType());
        if (provider == null) {
            return request.objective();
        }

        String context = safeBuildContext(provider, request);
        if (context.isBlank()) {
            return request.objective();
        }

        return request.objective() + "\n\n" + provider.contextHeader() + "\n" + truncate(context, request);
    }

    /**
     * Una caída de Supabase no puede tumbar la corrida del agente: el agente sigue, sin
     * contexto, y lo reportará como missingData. Preferimos una respuesta honesta y limitada
     * antes que un 500 en cara del usuario.
     */
    private String safeBuildContext(AgentContextProvider provider, AgentRunRequest request) {
        try {
            String context = provider.buildContext(request);
            return context == null ? "" : context;
        } catch (RuntimeException e) {
            log.error("Fuente de contexto de {} falló, el agente corre sin contexto externo: {}",
                    request.agentType(), e.getMessage());
            return "";
        }
    }

    /**
     * Techo duro de contexto. Sin esto, una tabla que crece (10k prospectos, 5k avisos)
     * desborda la ventana del modelo y produce JSON truncado — el mismo fallo que ya vimos
     * con el modelo liviano, pero silencioso y en producción.
     */
    private String truncate(String context, AgentRunRequest request) {
        if (context.length() <= maxContextChars) {
            return context;
        }

        log.warn("Contexto de {} truncado: {} caracteres excedían el presupuesto de {}",
                request.agentType(), context.length(), maxContextChars);

        return context.substring(0, maxContextChars)
                + "\n[...contexto truncado por límite de tamaño; los datos mostrados son parciales]";
    }
}
