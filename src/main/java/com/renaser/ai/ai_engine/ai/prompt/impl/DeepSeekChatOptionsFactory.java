package com.renaser.ai.ai_engine.ai.prompt.impl;

import com.renaser.ai.ai_engine.ai.model.AgentType;
import com.renaser.ai.ai_engine.ai.prompt.AgentModelSelector;
import com.renaser.ai.ai_engine.ai.prompt.ChatOptionsFactory;
import com.renaser.ai.ai_engine.ai.prompt.PoliticaDeterminismo;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Opciones de chat para DeepSeek.
 * <p>
 * Cuatro decisiones que conviene no perder de vista:
 * <p>
 * 1. responseFormat es JSON_OBJECT, no JSON Schema. DeepSeek no soporta schema estricto, así
 * que el schema del envelope viaja en el prompt (lo inyecta el converter de Spring AI) y el
 * modelo solo garantiza que la salida sea JSON sintácticamente válido, no que respete el
 * contrato. Por eso base-system-prompt.md incluye la palabra "json" y un ejemplo: son
 * requisito documentado del JSON mode de DeepSeek, no adorno.
 * <p>
 * 2. maxTokens es explícito. Un envelope truncado a mitad de camino es JSON inválido, y ese
 * fue exactamente el modo de falla que descartó a qwen3:0.6b con su ventana de 4096.
 * <p>
 * 3. <b>La temperatura se fija siempre</b>, y quién la decide no es esta clase: es
 * {@link PoliticaDeterminismo}. Antes no se fijaba en ningún sitio del proyecto y todas las
 * llamadas salían con el valor por defecto del proveedor, que no es cero; el mismo currículum
 * calificado dos veces dio 71,00 y 58,50. <b>Lee el javadoc de
 * {@link PoliticaDeterminismoImpl} antes de tocar esto</b>: explica por qué unos agentes van a
 * cero y otros no, y —más importante— hasta dónde llega la temperatura cero y dónde deja de
 * llegar. Es necesaria pero no suficiente, y prometer más de eso sería mentir.
 * <p>
 * 4. <b>topP no se toca.</b> DeepSeek documenta que se mueve {@code temperature} o
 * {@code top_p}, no los dos. El proyecto nunca fijó {@code top_p} y se deja así.
 */
@Component
public class DeepSeekChatOptionsFactory implements ChatOptionsFactory {

    private static final ResponseFormat JSON_OBJECT = ResponseFormat.builder()
            .type(ResponseFormat.Type.JSON_OBJECT)
            .build();

    private final AgentModelSelector agentModelSelector;
    private final PoliticaDeterminismo politicaDeterminismo;
    private final Integer maxTokens;

    public DeepSeekChatOptionsFactory(AgentModelSelector agentModelSelector,
                                      PoliticaDeterminismo politicaDeterminismo,
                                      @Value("${renaser.ai.chat.max-tokens}") Integer maxTokens) {
        this.agentModelSelector = agentModelSelector;
        this.politicaDeterminismo = politicaDeterminismo;
        this.maxTokens = maxTokens;
    }

    @Override
    public ChatOptions.Builder<?> forAgent(AgentType agentType) {
        return opciones(agentType.name(), agentModelSelector.selectModel(agentType));
    }

    @Override
    public ChatOptions.Builder<?> paraAgenteDeSeleccion(String codigoAgente, String modelo) {
        return opciones(codigoAgente, modelo);
    }

    /**
     * El único sitio donde se arman las opciones.
     *
     * <p>Está así a propósito: mientras cada camino de llamada construía las suyas, la
     * temperatura podía quedar fijada en uno y suelta en el otro sin que nada fallara —que es
     * exactamente lo que pasaba—. Con un solo constructor, olvidarse de la temperatura en un
     * camino ya no es posible.
     *
     * <p>La semilla no aparece aquí porque no hay dónde ponerla: el builder de
     * {@code DeepSeekChatOptions} de Spring AI 2.0 no tiene {@code seed}, y la API de DeepSeek
     * tampoco lo acepta. Está decidida y guardada en {@link PoliticaDeterminismo#semilla()}
     * para el día que se pueda mandar, y hasta entonces no está en efecto.
     */
    private ChatOptions.Builder<?> opciones(String codigoAgente, String modelo) {
        return DeepSeekChatOptions.builder()
                .responseFormat(JSON_OBJECT)
                .model(modelo)
                .maxTokens(maxTokens)
                .temperature(politicaDeterminismo.temperaturaDe(codigoAgente));
    }
}
