package com.renaser.ai.ai_engine.ai.context.impl;

import com.renaser.ai.ai_engine.ai.context.AgentContextProvider;
import com.renaser.ai.ai_engine.ai.dto.AgentRunRequest;
import com.renaser.ai.ai_engine.ai.model.AgentType;
import com.renaser.ai.ai_engine.ai.rag.DocumentRetrievalService;
import com.renaser.ai.ai_engine.ai.rag.SearchResultResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class KnowledgeContextProvider implements AgentContextProvider {

    private final DocumentRetrievalService documentRetrievalService;

    @Override
    public AgentType agentType() {
        return AgentType.KNOWLEDGE;
    }

    @Override
    public String contextHeader() {
        return "Contexto recuperado de la base de conocimiento (search_knowledge). "
                + "Cita el documentId como evidencia; si no responde la pregunta, dilo en missingData:";
    }

    @Override
    public String buildContext(AgentRunRequest request) {
        List<SearchResultResponse> results = documentRetrievalService.search(request.objective());

        // El id va explícito para que el agente pueda citar evidencia trazable en vez de "N/A".
        return results.stream()
                .map(r -> "[documentId=%s]\n%s".formatted(r.id(), r.text()))
                .collect(Collectors.joining("\n---\n"));
    }
}
