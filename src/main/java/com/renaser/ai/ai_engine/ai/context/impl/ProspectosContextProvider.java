package com.renaser.ai.ai_engine.ai.context.impl;

import com.renaser.ai.ai_engine.ai.context.AgentContextProvider;
import com.renaser.ai.ai_engine.ai.dto.AgentRunRequest;
import com.renaser.ai.ai_engine.ai.model.AgentType;
import com.renaser.ai.ai_engine.ai.supabase.ProspectoDataProvider;
import com.renaser.ai.ai_engine.ai.supabase.ProspectoRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProspectosContextProvider implements AgentContextProvider {

    private final ProspectoDataProvider prospectoDataProvider;

    @Override
    public AgentType agentType() {
        return AgentType.GROWTH;
    }

    @Override
    public String contextHeader() {
        return "Embudo comercial agregado por etapa (datos reales):";
    }

    /**
     * Growth razona sobre el embudo, no sobre prospectos individuales: mandamos el agregado
     * por etapa y no la lista cruda. Con 5 prospectos daba igual; con 50.000 la lista cruda
     * desbordaría el contexto y además diluiría la señal que el agente necesita.
     */
    @Override
    public String buildContext(AgentRunRequest request) {
        List<ProspectoRecord> prospectos = prospectoDataProvider.getProspectos();
        if (prospectos.isEmpty()) {
            return "";
        }

        Map<String, List<ProspectoRecord>> porEtapa = prospectos.stream()
                .collect(Collectors.groupingBy(p -> p.etapa() == null ? "sin etapa" : p.etapa()));

        String etapas = porEtapa.entrySet().stream()
                .map(e -> "Etapa %s: %d prospectos, valor total %s".formatted(
                        e.getKey(), e.getValue().size(), sumValor(e.getValue())))
                .collect(Collectors.joining("\n"));

        return etapas + "\nTotal de prospectos en el embudo: " + prospectos.size();
    }

    private BigDecimal sumValor(List<ProspectoRecord> prospectos) {
        return prospectos.stream()
                .map(p -> p.valor() == null ? BigDecimal.ZERO : p.valor())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
