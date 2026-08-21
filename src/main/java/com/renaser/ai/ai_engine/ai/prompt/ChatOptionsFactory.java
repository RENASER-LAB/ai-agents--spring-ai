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
 * <p>
 * <b>Hay dos métodos porque hay dos caminos de llamada.</b> El motor de agentes entra por
 * {@link #forAgent(AgentType)} con su enum; los seis agentes de selección entran por
 * {@link #paraAgenteDeSeleccion(String, String)} con un código de texto y con el modelo ya
 * elegido, porque ahí la pasada rápida y la fina usan modelos distintos y eso lo decide el
 * trabajo, no el agente. Lo que <b>no</b> puede diferir entre los dos caminos es la política
 * de temperatura: por eso los dos la piden a {@link PoliticaDeterminismo} y ninguno arma sus
 * opciones por su cuenta.
 */
public interface ChatOptionsFactory {

    ChatOptions.Builder<?> forAgent(AgentType agentType);

    /**
     * Las opciones de uno de los agentes de selección.
     *
     * @param codigoAgente {@code DATOS_CV}, {@code EVIDENCIA_CV}, {@code EVALUADOR},
     *                     {@code POTENCIAL_RIESGO}, {@code PRUEBA_PUESTO} o
     *                     {@code SIMULACION}. Decide la temperatura.
     * @param modelo       el modelo que hay que pedir. Viaja como parámetro y no se resuelve
     *                     aquí porque es lo que distingue la pasada rápida de la fina.
     */
    ChatOptions.Builder<?> paraAgenteDeSeleccion(String codigoAgente, String modelo);
}
