package com.renaser.ai.ai_engine.ai.prompt.impl;

import com.renaser.ai.ai_engine.ai.model.AgentType;
import com.renaser.ai.ai_engine.ai.prompt.AgentModelSelector;
import com.renaser.ai.ai_engine.ai.prompt.PoliticaDeterminismo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.ResponseFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Corre sin levantar Spring ni tocar la base: ese es el punto de haber sacado la
 * construcción de opciones fuera de AgentInvoker.
 */
@DisplayName("Las opciones que se le mandan a DeepSeek")
class DeepSeekChatOptionsFactoryTest {

    private static final int MAX_TOKENS = 4096;

    private static final PoliticaDeterminismo POLITICA =
            new PoliticaDeterminismoImpl(0.0, 1.0, 20260820);

    private DeepSeekChatOptionsFactory factory(AgentModelSelector selector) {
        return new DeepSeekChatOptionsFactory(selector, POLITICA, MAX_TOKENS);
    }

    private DeepSeekChatOptions optionsFor(AgentType agentType, AgentModelSelector selector) {
        return (DeepSeekChatOptions) factory(selector).forAgent(agentType).build();
    }

    private DeepSeekChatOptions optionsDeSeleccion(String codigoAgente) {
        return (DeepSeekChatOptions) factory(agentType -> "deepseek-v4-pro")
                .paraAgenteDeSeleccion(codigoAgente, "deepseek-chat")
                .build();
    }

    @Test
    void fuerzaJsonModeEnTodosLosAgentes() {
        // DeepSeek no tiene JSON Schema estricto: si el response_format no sale como
        // json_object, el envelope llega como prosa y el converter no tiene nada que parsear.
        AgentModelSelector selector = agentType -> "deepseek-v4-pro";

        for (AgentType agentType : AgentType.values()) {
            assertThat(optionsFor(agentType, selector).getResponseFormat().getType())
                    .as("responseFormat de %s", agentType)
                    .isEqualTo(ResponseFormat.Type.JSON_OBJECT);
        }
    }

    @Test
    void aplicaElTechoDeTokensParaQueElEnvelopeNoSalgaTruncado() {
        AgentModelSelector selector = agentType -> "deepseek-v4-pro";

        assertThat(optionsFor(AgentType.CEO, selector).getMaxTokens()).isEqualTo(MAX_TOKENS);
    }

    @Test
    void respetaElModeloQueResuelveElSelectorPorAgente() {
        // El override por agente es el mecanismo que mantiene a ORCHESTRATOR en
        // deepseek-v4-pro. Si la factory ignorara al selector, poblar MODEL_OVERRIDES
        // no tendría ningún efecto y el ahorro sería silenciosamente cero.
        AgentModelSelector selector = agentType ->
                agentType == AgentType.NARRATIVE_MESSAGE ? "deepseek-v4-flash" : "deepseek-v4-pro";

        assertThat(optionsFor(AgentType.NARRATIVE_MESSAGE, selector).getModel())
                .isEqualTo("deepseek-v4-flash");
        assertThat(optionsFor(AgentType.ORCHESTRATOR, selector).getModel())
                .isEqualTo("deepseek-v4-pro");
    }

    // ========================================================================
    // Temperatura: lo que faltaba y hacía que una nota no se pudiera repetir
    // ========================================================================

    /**
     * Antes esto no se fijaba en ningún sitio y la llamada salía con el valor por defecto del
     * proveedor. Si vuelve a salir null, vuelve el problema y nada falla: por eso se
     * comprueba que esté puesto, no solo que valga cero.
     */
    @ParameterizedTest
    @ValueSource(strings = {"DATOS_CV", "EVIDENCIA_CV", "EVALUADOR", "POTENCIAL_RIESGO",
            "PRUEBA_PUESTO"})
    void losAgentesQuePuntuanPidenTemperaturaCero(String codigoAgente) {
        assertThat(optionsDeSeleccion(codigoAgente).getTemperature())
                .as("temperatura de %s", codigoAgente)
                .isNotNull()
                .isEqualTo(0.0);
    }

    @Test
    void elAgenteQueEscribeLasPreguntasNoPideTemperaturaCero() {
        assertThat(optionsDeSeleccion("SIMULACION").getTemperature())
                .isNotNull()
                .isGreaterThan(0.0);
    }

    @Test
    void laTemperaturaTambienSeFijaEnElCaminoDelMotorDeAgentes() {
        // Son dos caminos de llamada distintos. Que uno la fije y el otro no es exactamente
        // el agujero que había, así que se comprueban los dos.
        AgentModelSelector selector = agentType -> "deepseek-v4-pro";

        for (AgentType agentType : AgentType.values()) {
            assertThat(optionsFor(agentType, selector).getTemperature())
                    .as("temperatura de %s", agentType)
                    .isNotNull();
        }
        assertThat(optionsFor(AgentType.AUDITOR, selector).getTemperature()).isEqualTo(0.0);
    }

    /**
     * DeepSeek documenta que se mueve {@code temperature} o {@code top_p}, no los dos. Como
     * aquí se fija la temperatura, {@code top_p} tiene que quedarse sin tocar.
     */
    @Test
    void noSeTocaTopPPorqueYaSeMueveLaTemperatura() {
        AgentModelSelector selector = agentType -> "deepseek-v4-pro";

        assertThat(optionsFor(AgentType.CEO, selector).getTopP()).isNull();
        assertThat(optionsDeSeleccion("EVALUADOR").getTopP()).isNull();
    }

    @Test
    void elModeloDeLaPasadaViajaComoParametroYNoLoDecideLaFactory() {
        // La pasada rápida y la fina usan modelos distintos sobre el mismo agente: si la
        // factory lo resolviera por su cuenta, la criba rápida dejaría de ser rápida.
        DeepSeekChatOptions opciones = (DeepSeekChatOptions)
                factory(agentType -> "deepseek-v4-pro")
                        .paraAgenteDeSeleccion("EVIDENCIA_CV", "deepseek-chat")
                        .build();

        assertThat(opciones.getModel()).isEqualTo("deepseek-chat");
    }
}
