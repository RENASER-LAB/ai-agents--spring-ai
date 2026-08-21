package com.renaser.ai.ai_engine.ai.prompt.impl;

import com.renaser.ai.ai_engine.ai.model.AgentType;
import com.renaser.ai.ai_engine.ai.prompt.PoliticaDeterminismo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La regla que separa a los agentes que puntúan de los que redactan.
 *
 * <p>Es la clase que decide si una nota se puede repetir, así que se prueba sola y sin Spring:
 * si esto se rompe, se tiene que ver aquí y no tres capas más arriba.
 */
@DisplayName("Cuánta libertad se le da al modelo en cada agente")
class PoliticaDeterminismoImplTest {

    private static final double TEMPERATURA_PUNTUACION = 0.0;
    private static final double TEMPERATURA_REDACCION = 1.0;
    private static final int SEMILLA = 20260820;

    private final PoliticaDeterminismo politica = new PoliticaDeterminismoImpl(
            TEMPERATURA_PUNTUACION, TEMPERATURA_REDACCION, SEMILLA);

    /**
     * Los cinco que ponen un número. Si alguno se sale de temperatura cero, dos corridas del
     * mismo currículum vuelven a poder dar 71,00 y 58,50, que es de donde viene todo esto.
     */
    @ParameterizedTest
    @ValueSource(strings = {"DATOS_CV", "EVIDENCIA_CV", "EVALUADOR", "POTENCIAL_RIESGO",
            "PRUEBA_PUESTO"})
    void losAgentesQuePuntuanVanATemperaturaCero(String codigoAgente) {
        assertThat(politica.temperaturaDe(codigoAgente))
                .as("temperatura de %s", codigoAgente)
                .isZero();
        assertThat(politica.redactaParaUnaPersona(codigoAgente)).isFalse();
    }

    /**
     * SIMULACION escribe las preguntas de la conversación final. A cero saldrían planas y,
     * peor, iguales entre candidatos: ante insumos parecidos el modelo escogería siempre la
     * misma formulación, y dos personas llegarían a la entrevista con el mismo guion.
     */
    @Test
    void elAgenteQueEscribeLasPreguntasNoVaACero() {
        assertThat(politica.redactaParaUnaPersona("SIMULACION")).isTrue();
        assertThat(politica.temperaturaDe("SIMULACION"))
                .isEqualTo(TEMPERATURA_REDACCION)
                .isGreaterThan(TEMPERATURA_PUNTUACION);
    }

    @Test
    void elAgenteDeMensajesDelMotorTampocoVaACero() {
        // NARRATIVE_MESSAGE convierte una intención aprobada en texto de marca. Es el mismo
        // caso que SIMULACION: lo que produce lo lee una persona, no una hoja de cálculo.
        assertThat(politica.temperaturaDe(AgentType.NARRATIVE_MESSAGE.name()))
                .isEqualTo(TEMPERATURA_REDACCION);
    }

    @Test
    void elRestoDeAgentesDelMotorVaACero() {
        for (AgentType agentType : AgentType.values()) {
            if (agentType == AgentType.NARRATIVE_MESSAGE) {
                continue;
            }
            assertThat(politica.temperaturaDe(agentType.name()))
                    .as("temperatura de %s", agentType)
                    .isZero();
        }
    }

    @Test
    void unAgenteDesconocidoCaeDelLadoSeguro() {
        // Que un agente nuevo salga determinista por descuido es un problema pequeño; que
        // salga suelto por descuido es el problema que se está arreglando.
        assertThat(politica.temperaturaDe("AGENTE_QUE_TODAVIA_NO_EXISTE")).isZero();
        assertThat(politica.temperaturaDe(null)).isZero();
    }

    /**
     * La semilla está decidida y es la misma siempre.
     *
     * <p><b>Esta prueba no demuestra que las corridas estén sembradas</b>, y no puede: Spring
     * AI 2.0 no tiene dónde poner un {@code seed} en las opciones de DeepSeek y la API del
     * proveedor tampoco lo acepta. Comprueba que el valor esté fijado y que no dependa del
     * agente, para el día que se pueda mandar.
     */
    @Test
    void laSemillaEsUnaSolaYNoCambiaEntreAgentes() {
        assertThat(politica.semilla()).isEqualTo(SEMILLA);

        PoliticaDeterminismo otra = new PoliticaDeterminismoImpl(
                TEMPERATURA_PUNTUACION, TEMPERATURA_REDACCION, 7);
        assertThat(otra.semilla()).as("es configurable").isEqualTo(7);
    }
}
