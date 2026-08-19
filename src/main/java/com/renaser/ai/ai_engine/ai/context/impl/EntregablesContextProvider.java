package com.renaser.ai.ai_engine.ai.context.impl;

import com.renaser.ai.ai_engine.ai.context.AgentContextProvider;
import com.renaser.ai.ai_engine.ai.dto.AgentRunRequest;
import com.renaser.ai.ai_engine.ai.model.AgentType;
import com.renaser.ai.ai_engine.ai.supabase.EntregableDataProvider;
import com.renaser.ai.ai_engine.ai.supabase.EntregableRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class EntregablesContextProvider implements AgentContextProvider {

    private final EntregableDataProvider entregableDataProvider;

    @Override
    public AgentType agentType() {
        return AgentType.AUDITOR;
    }

    @Override
    public String contextHeader() {
        return "Entregables pendientes de revisión, los más antiguos primero (datos reales):";
    }

    @Override
    public String buildContext(AgentRunRequest request) {
        return entregableDataProvider.getEntregablesPendientes().stream()
                .map(this::describe)
                .collect(Collectors.joining("\n"));
    }

    private String describe(EntregableRecord e) {
        return "\"%s\" (%s, v%s) pendiente de revisión desde %s".formatted(
                e.nombre(), e.tipo(), e.version(), e.createdAt());
    }
}
