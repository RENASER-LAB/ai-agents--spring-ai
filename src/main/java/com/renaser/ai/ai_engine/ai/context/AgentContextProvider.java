package com.renaser.ai.ai_engine.ai.context;

import com.renaser.ai.ai_engine.ai.dto.AgentRunRequest;
import com.renaser.ai.ai_engine.ai.model.AgentType;

/**
 * Fuente de contexto externo para un agente concreto (tabla de Supabase, RAG, o en el
 * futuro una tool del sistema del equipo de datos).
 *
 * Conectar un agente nuevo = crear una implementación nueva. No se toca ninguna clase
 * existente (Open/Closed): AgentContextResolver las descubre por inyección de Spring.
 */
public interface AgentContextProvider {

    // Agente al que alimenta esta fuente. Un agente = un provider como máximo.
    AgentType agentType();

    // Contexto ya formateado para inyectar en el prompt. Cadena vacía = sin contexto
    // disponible (el agente correrá solo con el objective y lo reportará en missingData).
    String buildContext(AgentRunRequest request);

    // Encabezado que precede al contexto en el prompt, para que el modelo sepa de dónde
    // salió el dato y pueda citarlo como evidencia.
    String contextHeader();
}
