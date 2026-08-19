package com.renaser.ai.ai_engine.ai.context.impl;

import com.renaser.ai.ai_engine.ai.context.AgentContextProvider;
import com.renaser.ai.ai_engine.ai.dto.AgentRunRequest;
import com.renaser.ai.ai_engine.ai.model.AgentType;
import com.renaser.ai.ai_engine.ai.supabase.EventoDataProvider;
import com.renaser.ai.ai_engine.ai.supabase.EventoRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class EventoContextProvider implements AgentContextProvider {

    private final EventoDataProvider eventoDataProvider;

    @Override
    public AgentType agentType() {
        return AgentType.EVENT;
    }

    @Override
    public String contextHeader() {
        return "Datos del evento (reales, evidence_id = id del evento):";
    }

    // El entityId del request identifica al evento (columna "nombre" de la tabla eventos).
    @Override
    public String buildContext(AgentRunRequest request) {
        return eventoDataProvider.getEventoByNombre(request.entityId()).stream()
                .map(this::describe)
                .collect(Collectors.joining("\n"));
    }

    private String describe(EventoRecord e) {
        return "[evidence_id=%s] %s: ingresos %s, egresos %s".formatted(
                e.id(), e.nombre(), e.ingresos(), e.egresos());
    }
}
