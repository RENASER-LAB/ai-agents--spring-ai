package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.dto.RespuestaModelo;
import com.renaser.ai.ai_engine.ai.model.Agente;
import com.renaser.ai.ai_engine.ai.model.EjecucionIa;
import com.renaser.ai.ai_engine.ai.model.InstruccionIa;
import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.repository.AgenteRepository;
import com.renaser.ai.ai_engine.ai.repository.EjecucionIaRepository;
import com.renaser.ai.ai_engine.ai.repository.InstruccionIaRepository;
import com.renaser.ai.ai_engine.ai.repository.TarifaModeloRepository;
import com.renaser.ai.ai_engine.ai.service.ClienteModelo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La fecha de hoy en cada llamada (spec la-ia-sabe-que-dia-es-hoy, AC-01 a AC-04).
 *
 * <p>Sin ella el modelo razona con la fecha de su entrenamiento y marca como «futura» una
 * experiencia de febrero de 2026 que ya ocurrió. El reloj se fija en cada prueba: ninguna depende
 * del día en que se corra.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("La fecha de hoy en el mensaje de cada agente")
class FechaDeHoyEnElPromptTest {

    /** 16/11/2026 a las 10:00 de Lima (15:00 UTC). */
    private static final Instant DIECISEIS_DE_NOVIEMBRE = Instant.parse("2026-11-16T15:00:00Z");
    private static final String FORMATO = "Devuelve un JSON con «ok».";

    @Mock private AgenteRepository agentes;
    @Mock private InstruccionIaRepository instrucciones;
    @Mock private EjecucionIaRepository ejecuciones;
    @Mock private TarifaModeloRepository tarifas;

    private final List<String> sistemas = new ArrayList<>();

    private EjecutorAgenteIaImpl ejecutor(Instant ahora, String codigo, String instruccion) {
        when(agentes.findById(codigo)).thenReturn(Optional.of(
                Agente.builder().codigo(codigo).version(1).esActivo(true).build()));
        when(instrucciones.findFirstByAgenteCodigoAndEsActivaTrue(codigo)).thenReturn(Optional.of(
                InstruccionIa.builder().id(7L).agenteCodigo(codigo).texto(instruccion).esActiva(true).build()));
        when(ejecuciones.save(any(EjecucionIa.class))).thenAnswer(llamada -> {
            EjecucionIa fila = llamada.getArgument(0);
            fila.setId(1L);
            return fila;
        });
        ClienteModelo espia = new ClienteModelo() {
            @Override
            public RespuestaModelo preguntar(String agenteCodigo, String sistema, String contenido) {
                return preguntar(agenteCodigo, sistema, contenido, true);
            }

            @Override
            public RespuestaModelo preguntar(String agenteCodigo, String sistema, String contenido,
                                             boolean razona) {
                sistemas.add(sistema);
                return new RespuestaModelo("{\"ok\":true}", "deepseek-flash", "deepseek", "v1", 10, 5);
            }
        };
        return new EjecutorAgenteIaImpl(agentes, instrucciones, ejecuciones, espia,
                new CalculadoraCostoIa(tarifas), JsonMapper.builder().build(),
                new FechaDeHoyParaLaIa(Clock.fixed(ahora, ZoneOffset.UTC)));
    }

    private static void llamar(EjecutorAgenteIaImpl ejecutor, String codigo, Object insumo) {
        ejecutor.ejecutar(TrabajoIa.builder().id(1L).organizacionId(1L).agenteCodigo(codigo).build(),
                "Probar la fecha", FORMATO, insumo, Map.class, false);
    }

    private String envioGuardado() {
        ArgumentCaptor<EjecucionIa> captor = ArgumentCaptor.forClass(EjecucionIa.class);
        verify(ejecuciones).save(captor.capture());
        return captor.getValue().getEnvio();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"DATOS_CV", "EVIDENCIA_CV", "POTENCIAL_RIESGO", "PRUEBA_PUESTO",
            "SIMULACION", "REDACTOR_INFORME"})
    @DisplayName("AC-01 · cada agente lleva el bloque en su envío guardado, fuera de la instrucción editable")
    void cadaAgenteLlevaLaFecha(String codigo) {
        String instruccion = "Instrucción del agente " + codigo + ".";
        llamar(ejecutor(DIECISEIS_DE_NOVIEMBRE, codigo, instruccion), codigo, Map.of("dato", "x"));

        String sistema = sistemas.getFirst();
        assertThat(sistema).contains("Fecha de hoy: 16 de noviembre de 2026 (2026-11-16, hora de Lima).");
        // Entre la instrucción y el formato: la instrucción sigue siendo el prefijo.
        assertThat(sistema).startsWith(instruccion).endsWith(FORMATO);
        assertThat(sistema.indexOf("Fecha de hoy:"))
                .isGreaterThan(sistema.indexOf(instruccion))
                .isLessThan(sistema.indexOf(FORMATO));

        String envio = envioGuardado();
        assertThat(envio).contains("Fecha de hoy: 16 de noviembre de 2026 (2026-11-16, hora de Lima).");
        assertThat(envio.indexOf("Fecha de hoy:"))
                .as("la fecha va en la parte de la instrucción del registro, no entre los datos")
                .isLessThan(envio.indexOf("=== DATOS · "));
    }

    @Test
    @DisplayName("AC-02 · una instrucción editada que no habla de fechas no la quita")
    void sobreviveAUnaInstruccionEditada() {
        String editada = "Califica el currículum con criterio. No menciones nada más.";
        llamar(ejecutor(DIECISEIS_DE_NOVIEMBRE, "EVIDENCIA_CV", editada), "EVIDENCIA_CV", Map.of());

        assertThat(sistemas.getFirst()).contains("Fecha de hoy: 16 de noviembre de 2026");
        assertThat(envioGuardado()).contains("Fecha de hoy: 16 de noviembre de 2026");
    }

    @Test
    @DisplayName("AC-03 · un CV con «febrero 2026 – actualidad» calificado el 16/11/2026: el bloque dice 2026-11-16 y que lo anterior ya ocurrió")
    void elCvConActualidad() {
        llamar(ejecutor(DIECISEIS_DE_NOVIEMBRE, "EVIDENCIA_CV", "Califica el CV."), "EVIDENCIA_CV",
                Map.of("curriculum", "Supervisor de planta, febrero 2026 – actualidad"));

        String sistema = sistemas.getFirst();
        assertThat(sistema).contains("2026-11-16")
                .contains("Toda fecha igual o anterior a hoy ya ocurrió: no es futura ni imposible.")
                .contains("«Actualidad» o «a la fecha» en un CV significa hasta hoy.")
                .contains("Calcula duraciones y antigüedades con esta fecha.");
        // El CV viaja en los datos, nunca pegado a la instrucción.
        assertThat(sistema).doesNotContain("Supervisor de planta");
    }

    @Test
    @DisplayName("AC-04 · con el reloj fijado a otra fecha, el bloque usa esa y no la del equipo")
    void usaElRelojDelSistema() {
        llamar(ejecutor(Instant.parse("2031-03-01T17:00:00Z"), "DATOS_CV", "Saca los datos."),
                "DATOS_CV", Map.of());

        assertThat(sistemas.getFirst()).contains("Fecha de hoy: 1 de marzo de 2031 (2031-03-01, hora de Lima).");
    }

    @Test
    @DisplayName("la fecha es la de Lima: a las 23:30 de Lima ya es mañana en UTC y el bloque sigue diciendo hoy")
    void cruzaLaMedianocheEnUtc() {
        // 17/11 04:30 UTC = 16/11 23:30 en Lima.
        llamar(ejecutor(Instant.parse("2026-11-17T04:30:00Z"), "POTENCIAL_RIESGO", "Arma el retrato."),
                "POTENCIAL_RIESGO", Map.of());

        assertThat(sistemas.getFirst()).contains("(2026-11-16, hora de Lima)")
                .doesNotContain("2026-11-17");
    }

    @Test
    @DisplayName("el mismo bloque para todos: no depende del agente ni de la instrucción")
    void igualParaTodos() {
        FechaDeHoyParaLaIa fecha = new FechaDeHoyParaLaIa(Clock.fixed(DIECISEIS_DE_NOVIEMBRE, ZoneOffset.UTC));
        assertThat(fecha.bloque()).isEqualTo(
                "Fecha de hoy: 16 de noviembre de 2026 (2026-11-16, hora de Lima). Toda fecha igual o "
                        + "anterior a hoy ya ocurrió: no es futura ni imposible. «Actualidad» o «a la "
                        + "fecha» en un CV significa hasta hoy. Calcula duraciones y antigüedades con "
                        + "esta fecha.");
    }
}
