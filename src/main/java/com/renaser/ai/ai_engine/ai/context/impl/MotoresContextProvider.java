package com.renaser.ai.ai_engine.ai.context.impl;

import com.renaser.ai.ai_engine.ai.context.AgentContextProvider;
import com.renaser.ai.ai_engine.ai.dto.AgentRunRequest;
import com.renaser.ai.ai_engine.ai.model.AgentType;
import com.renaser.ai.ai_engine.ai.supabase.MotorDataProvider;
import com.renaser.ai.ai_engine.ai.supabase.MotorRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MotoresContextProvider implements AgentContextProvider {

    private final MotorDataProvider motorDataProvider;

    @Override
    public AgentType agentType() {
        return AgentType.CONSULTING;
    }

    @Override
    public String contextHeader() {
        return "Estado de los 8 motores estratégicos (datos reales, evidence_id = motor_N):";
    }

    @Override
    public String buildContext(AgentRunRequest request) {
        return motorDataProvider.getMotores().stream()
                .map(this::describe)
                .collect(Collectors.joining("\n"));
    }

    private String describe(MotorRecord m) {
        return "[evidence_id=motor_%d] Motor %d - %s: %s = %s (meta %s), estado %s".formatted(
                m.n(), m.n(), m.nombre(), m.kpi(), m.valor(), m.meta(), m.estado());
    }
}
