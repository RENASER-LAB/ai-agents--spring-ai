package com.renaser.ai.ai_engine.ai.rag;

import java.util.List;

public interface DocumentRetrievalService {

    // Busca los fragmentos más relevantes para una consulta dentro del vector store
    List<SearchResultResponse> search(String query);
}
