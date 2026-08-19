package com.renaser.ai.ai_engine.ai.context.impl;

import com.renaser.ai.ai_engine.ai.context.AgentContextProvider;
import com.renaser.ai.ai_engine.ai.dto.AgentRunRequest;
import com.renaser.ai.ai_engine.ai.model.AgentType;
import com.renaser.ai.ai_engine.ai.supabase.ActividadDataProvider;
import com.renaser.ai.ai_engine.ai.supabase.ActividadRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ActividadesContextProvider implements AgentContextProvider {

    private final ActividadDataProvider actividadDataProvider;

    @Override
    public AgentType agentType() {
        return AgentType.OPERATIONS;
    }

    @Override
    public String contextHeader() {
        return "Actividades bloqueadas, priorizadas por prioridad y antigüedad (datos reales):";
    }

    @Override
    public String buildContext(AgentRunRequest request) {
        return actividadDataProvider.getActividadesBloqueadas().stream()
                .map(this::describe)
                .collect(Collectors.joining("\n"));
    }

    private String describe(ActividadRecord a) {
        return "\"%s\" (%s, prioridad %s, avance %s%%): %s".formatted(
                a.titulo(), a.estado(), a.prioridad(), a.avance(), a.bloqueo());
    }
}
