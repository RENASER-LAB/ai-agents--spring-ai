package com.renaser.ai.ai_engine.ai.prompt.impl;

import com.renaser.ai.ai_engine.ai.model.AgentType;
import com.renaser.ai.ai_engine.ai.prompt.AgentModelSelector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AgentModelSelectorImpl implements AgentModelSelector {

    // El modelo por defecto es deepseek-v4-flash; este mapa lista las excepciones que
    // justifican pagar deepseek-v4-pro, 3.4x más caro por corrida ($0.0035 contra $0.0120,
    // medido el 2026-08-16 sobre 20 corridas end-to-end de cada modelo).
    //
    // La tasa de parseo no los separa: ambos sostuvieron el contrato 20/20 sin un solo
    // reintento. Lo que separa a ORCHESTRATOR es cómo se consume su salida.
    // AgentHandoffPublisher ordena routing[] por priority y corta la cola al agotar
    // MAX_AGENT_RUNS, así que con prioridades planas ese corte se vuelve arbitrario y puede
    // descartar al agente que más importaba. En la corrida medida, Flash aplanó las tres
    // rutas del Orchestrator a p1 mientras Pro las diferenció (p1 cobranza, p2 el resto).
    //
    // Fue una sola observación y Flash diferenció bien en 5 de los 6 agentes que rutean, así
    // que no alcanza para afirmar que es peor ruteando. Pero dejar un único agente en Pro
    // cuesta centavos, y es el que decide cuántos otros llegan a correr.
    private static final Map<AgentType, String> MODEL_OVERRIDES = Map.of(
            AgentType.ORCHESTRATOR, "deepseek-v4-pro");

    private final String defaultModel;

    public AgentModelSelectorImpl(@Value("${renaser.ai.chat.default-model}") String defaultModel) {
        this.defaultModel = defaultModel;
    }

    @Override
    public String selectModel(AgentType agentType) {
        return MODEL_OVERRIDES.getOrDefault(agentType, defaultModel);
    }
}
