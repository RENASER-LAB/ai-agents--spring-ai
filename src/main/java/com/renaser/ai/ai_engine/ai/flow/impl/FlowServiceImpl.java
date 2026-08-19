package com.renaser.ai.ai_engine.ai.flow.impl;

import com.renaser.ai.ai_engine.ai.flow.FlowService;
import com.renaser.ai.ai_engine.ai.flow.FlowStatus;
import com.renaser.ai.ai_engine.ai.flow.FlowStepResponse;
import com.renaser.ai.ai_engine.ai.flow.FlowTraceResponse;
import com.renaser.ai.ai_engine.ai.model.AgentRun;
import com.renaser.ai.ai_engine.ai.repository.AgentRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Proyección de lectura sobre agent_run: no ejecuta nada, sólo reconstruye la traza del flujo
 * a partir de las corridas ya persistidas.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlowServiceImpl implements FlowService {

    private final AgentRunRepository agentRunRepository;
    private final JsonMapper jsonMapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<FlowTraceResponse> getTrace(UUID flowId) {
        List<AgentRun> runs = agentRunRepository.findByFlowIdOrderByCreatedAtAsc(flowId);
        if (runs.isEmpty()) {
            return Optional.empty();
        }

        List<FlowStepResponse> steps = new ArrayList<>();
        for (int i = 0; i < runs.size(); i++) {
            steps.add(toStep(runs.get(i), i + 1));
        }

        AgentRun root = runs.getFirst();
        return Optional.of(new FlowTraceResponse(
                flowId,
                root.getEntityId(),
                root.getObjective(),
                overallStatus(steps),
                steps.size(),
                (int) steps.stream().filter(s -> s.status() == FlowStatus.COMPLETADO).count(),
                root.getCreatedAt(),
                totalDurationMs(runs),
                steps));
    }

    private FlowStepResponse toStep(AgentRun run, int step) {
        JsonNode output = parseOutput(run);

        return new FlowStepResponse(
                step,
                run.getId(),
                run.getParentRunId(),
                run.getDepth(),
                run.getAgentType(),
                stepStatus(run),
                run.getSeverity(),
                run.isRequiresHumanApproval(),
                durationMs(run),
                textsAt(output, "facts", "text"),
                textsAt(output, "missingData", "field"),
                textsAt(output, "routing", "agentId"),
                run.getErrorMessage(),
                output.path("payload"));
    }

    private FlowStatus stepStatus(AgentRun run) {
        if (run.getErrorMessage() != null) {
            return FlowStatus.FALLIDO;
        }
        return run.getOutputJson() == null ? FlowStatus.PENDIENTE : FlowStatus.COMPLETADO;
    }

    private FlowStatus overallStatus(List<FlowStepResponse> steps) {
        if (steps.stream().anyMatch(s -> s.status() == FlowStatus.PENDIENTE)) {
            return FlowStatus.EN_CURSO;
        }
        if (steps.stream().anyMatch(s -> s.status() == FlowStatus.FALLIDO)) {
            return FlowStatus.COMPLETADO_CON_ERRORES;
        }
        return FlowStatus.COMPLETADO;
    }

    private Long durationMs(AgentRun run) {
        if (run.getCreatedAt() == null || run.getFinishedAt() == null) {
            return null;
        }
        return Duration.between(run.getCreatedAt(), run.getFinishedAt()).toMillis();
    }

    private Long totalDurationMs(List<AgentRun> runs) {
        Instant start = runs.getFirst().getCreatedAt();
        Instant end = runs.stream()
                .map(AgentRun::getFinishedAt)
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);

        return (start == null || end == null) ? null : Duration.between(start, end).toMillis();
    }

    /**
     * El envelope lo produce un modelo de lenguaje: puede venir con campos ausentes o mal
     * escritos. La traza nunca debe romperse por eso — un campo que falta se traduce en lista
     * vacía, no en excepción.
     */
    private List<String> textsAt(JsonNode output, String arrayField, String textField) {
        List<String> values = new ArrayList<>();
        for (JsonNode item : output.path(arrayField)) {
            String value = item.path(textField).asString(null);
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private JsonNode parseOutput(AgentRun run) {
        if (run.getOutputJson() == null) {
            return jsonMapper.createObjectNode();
        }
        try {
            return jsonMapper.readTree(run.getOutputJson());
        } catch (JacksonException e) {
            log.warn("outputJson ilegible en la corrida {}: {}", run.getId(), e.getMessage());
            return jsonMapper.createObjectNode();
        }
    }
}
