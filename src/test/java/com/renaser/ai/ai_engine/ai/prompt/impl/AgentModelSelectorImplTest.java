package com.renaser.ai.ai_engine.ai.prompt.impl;

import com.renaser.ai.ai_engine.ai.model.AgentType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fija el reparto de modelos que salió de la medición del 2026-08-16: flash por defecto,
 * pro solo donde se justifica. Si alguien vacía MODEL_OVERRIDES el ahorro no cambia, pero
 * ORCHESTRATOR se degrada en silencio; y si alguien mete a los 15 en pro, el costo se
 * triplica sin que falle nada. Ninguno de los dos casos se nota sin este test.
 */
class AgentModelSelectorImplTest {

    private static final String FLASH = "deepseek-v4-flash";
    private static final String PRO = "deepseek-v4-pro";

    private final AgentModelSelectorImpl selector = new AgentModelSelectorImpl(FLASH);

    @Test
    void orchestratorSeQuedaEnProAunqueElDefaultSeaFlash() {
        // Su routing[] alimenta el corte por MAX_AGENT_RUNS de AgentHandoffPublisher: con
        // prioridades planas ese corte descarta agentes de forma arbitraria.
        assertThat(selector.selectModel(AgentType.ORCHESTRATOR)).isEqualTo(PRO);
    }

    @Test
    void losOtrosCatorceAgentesVanAlModeloPorDefecto() {
        for (AgentType agentType : AgentType.values()) {
            if (agentType == AgentType.ORCHESTRATOR) {
                continue;
            }
            assertThat(selector.selectModel(agentType))
                    .as("modelo de %s", agentType)
                    .isEqualTo(FLASH);
        }
    }

    @Test
    void elOverrideNoDependeDelDefaultConfigurado() {
        // Aunque alguien vuelva a poner pro como default, el mapa sigue siendo explícito.
        assertThat(new AgentModelSelectorImpl(PRO).selectModel(AgentType.ORCHESTRATOR)).isEqualTo(PRO);
    }
}
