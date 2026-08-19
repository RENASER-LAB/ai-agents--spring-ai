package com.renaser.ai.ai_engine.ai.context;

import com.renaser.ai.ai_engine.ai.dto.AgentRunRequest;

/**
 * Arma el mensaje de usuario que recibe el modelo: el objetivo de la corrida más el contexto
 * externo del agente, si tiene uno conectado.
 * <p>
 * Existe como contrato aparte de AgentContextProvider porque son dos responsabilidades
 * distintas: un provider sabe leer una fuente concreta, y el resolver decide cuál aplica,
 * qué hacer si falla y cuánto contexto cabe. AgentInvoker depende solo de esto, así que
 * puede probarse sin levantar los ocho providers ni tocar Supabase.
 */
public interface AgentContextResolver {

    String buildUserMessage(AgentRunRequest request);
}
