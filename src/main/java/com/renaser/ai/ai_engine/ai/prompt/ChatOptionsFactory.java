package com.renaser.ai.ai_engine.ai.prompt;

import com.renaser.ai.ai_engine.ai.model.AgentType;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * Construye las opciones de la llamada al modelo para un agente concreto.
 * <p>
 * Existe para que AgentInvoker no dependa de un tipo de opciones de proveedor. Antes
 * armaba OllamaChatOptions inline, así que cambiar de proveedor obligaba a editar la clase
 * que orquesta la llamada. Con esta interfaz, agregar o cambiar proveedor es una
 * implementación nueva y AgentInvoker queda intacto.
 * <p>
 * Devuelve el Builder sin construir, no el ChatOptions: es lo que espera
 * ChatClient.ChatClientRequestSpec#options en Spring AI 2.0.
 */
public interface ChatOptionsFactory {

    ChatOptions.Builder<?> forAgent(AgentType agentType);
}
