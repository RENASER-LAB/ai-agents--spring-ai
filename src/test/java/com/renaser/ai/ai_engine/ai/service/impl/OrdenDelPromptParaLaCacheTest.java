package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.dto.RespuestaModelo;
import com.renaser.ai.ai_engine.ai.model.Agente;
import com.renaser.ai.ai_engine.ai.model.EjecucionIa;
import com.renaser.ai.ai_engine.ai.model.InstruccionIa;
import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.repository.AgenteRepository;
import com.renaser.ai.ai_engine.ai.repository.EjecucionIaRepository;
import com.renaser.ai.ai_engine.ai.repository.InstruccionIaRepository;
import com.renaser.ai.ai_engine.ai.service.ClienteModelo;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.InsumoDatos;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoDatos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Que la parte que no cambia vaya delante, que es lo único que hace que la caché acierte.
 *
 * <p><b>Qué se está protegiendo.</b> DeepSeek guarda en disco los prefijos repetidos: lo que
 * entra por caché cuesta diez veces menos —$0,014 por millón de tokens en vez de $0,14— y el
 * primer token tarda mucho menos. No hay nada que activar, es automático, y por eso mismo es
 * frágil: <b>solo cuenta si el prefijo coincide desde el primer token</b>. Basta con que algo
 * variable se cuele delante de la instrucción para que el acierto sea cero en todas las
 * llamadas, y nada falle ni avise.
 *
 * <p>Justo el caso que más importa es el que más se repite: una tanda de cien currículums
 * contra la misma vacante son cien llamadas al mismo agente con la misma instrucción. Si la
 * instrucción va delante, se paga entera una vez.
 *
 * <p>Por eso la prueba no mira el texto del prompt: mira que <b>lo que se manda como
 * instrucción sea idéntico entre dos candidatos distintos</b>, y que lo que cambia viaje
 * aparte y después.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("El orden del prompt, para que la caché de DeepSeek acierte")
class OrdenDelPromptParaLaCacheTest {

    private static final String CODIGO = "DATOS_CV";

    @Mock private AgenteRepository agentes;
    @Mock private InstruccionIaRepository instrucciones;
    @Mock private EjecucionIaRepository ejecuciones;
    @Mock private com.renaser.ai.ai_engine.ai.repository.TarifaModeloRepository tarifas;

    /** Lo que se le mandó al modelo en cada llamada: la instrucción y los datos, aparte. */
    private record Envio(String instruccion, String contenido) {
    }

    private final List<Envio> envios = new ArrayList<>();

    private EjecutorAgenteIaImpl ejecutor;

    @BeforeEach
    void prepararElEjecutor() {
        ClienteModelo espia = new ClienteModelo() {
            @Override
            public RespuestaModelo preguntar(String agenteCodigo, String instruccion,
                                             String contenido) {
                return preguntar(agenteCodigo, instruccion, contenido, true);
            }

            @Override
            public RespuestaModelo preguntar(String agenteCodigo, String instruccion,
                                             String contenido, boolean razona) {
                envios.add(new Envio(instruccion, contenido));
                return new RespuestaModelo("{\"nombre\":\"Ana\"}", "deepseek-chat", "deepseek",
                        "v1", 100, 50);
            }
        };

        when(agentes.findById(CODIGO)).thenReturn(Optional.of(
                Agente.builder().codigo(CODIGO).version(1).esActivo(true).build()));
        when(instrucciones.findFirstByAgenteCodigoAndEsActivaTrue(CODIGO)).thenReturn(
                Optional.of(InstruccionIa.builder()
                        .id(7L)
                        .agenteCodigo(CODIGO)
                        .texto("Saca los datos del candidato del curriculum.")
                        .esActiva(true)
                        .build()));
        when(ejecuciones.save(any(EjecucionIa.class))).thenAnswer(llamada -> {
            EjecucionIa fila = llamada.getArgument(0);
            fila.setId(1L);
            return fila;
        });

        // La calculadora del costo con un repositorio vacío: sin tarifa el costo queda
        // nulo, que aquí da igual — esta prueba mira el orden del prompt, no el precio.
        ejecutor = new EjecutorAgenteIaImpl(agentes, instrucciones, ejecuciones, espia,
                new CalculadoraCostoIa(tarifas), JsonMapper.builder().build());
    }

    private void calificar(String curriculum) {
        ejecutor.ejecutar(
                TrabajoIa.builder().id(1L).organizacionId(1L).agenteCodigo(CODIGO).build(),
                "Sacar los datos del candidato",
                AgenteDatosCv.FORMATO,
                new InsumoDatos("Analista", curriculum),
                ResultadoDatos.class,
                false);
    }

    /**
     * Dos candidatos, la misma vacante, el mismo agente. La instrucción tiene que salir
     * idéntica: eso es el prefijo que DeepSeek puede reaprovechar.
     */
    @Test
    void laInstruccionEsIdenticaEntreDosCandidatosDistintos() {
        calificar("Ana Perez, ocho anos de experiencia en analisis de datos.");
        calificar("Luis Gomez, tres anos como desarrollador backend.");

        assertThat(envios).hasSize(2);
        assertThat(envios.get(0).instruccion())
                .as("el prefijo cacheable no puede cambiar de un candidato a otro")
                .isEqualTo(envios.get(1).instruccion());
    }

    /** Y lo que sí cambia tiene que ir aparte, no mezclado dentro de la instrucción. */
    @Test
    void elCurriculumViajaAparteYNoDentroDeLaInstruccion() {
        calificar("Ana Perez, ocho anos de experiencia en analisis de datos.");

        Envio envio = envios.getFirst();
        assertThat(envio.instruccion())
                .as("si el currículum se cuela en la instrucción, la caché no acierta nunca")
                .doesNotContain("Ana Perez");
        assertThat(envio.contenido()).contains("Ana Perez");
    }

    /**
     * La instrucción empieza por lo que administra Dirección y sigue por el formato; las dos
     * partes son fijas para el agente. Si algún día se le pegara algo del candidato delante,
     * esta prueba es la que lo tiene que cazar.
     */
    @Test
    void laInstruccionEmpiezaPorLaParteInvariableDelAgente() {
        calificar("Ana Perez, ocho anos de experiencia en analisis de datos.");

        assertThat(envios.getFirst().instruccion())
                .startsWith("Saca los datos del candidato del curriculum.")
                .endsWith(AgenteDatosCv.FORMATO);
    }

    /**
     * La bitácora de {@code ejecucion_ia} guarda el envío completo y en el mismo orden en que
     * se manda: instrucción primero, datos después. Importa para contestar un reclamo mirando
     * la fila en vez de adivinando.
     */
    @Test
    void laBitacoraGuardaElEnvioEnElMismoOrdenEnQueSeManda() {
        calificar("Ana Perez, ocho anos de experiencia en analisis de datos.");

        ArgumentCaptor<EjecucionIa> captor = ArgumentCaptor.forClass(EjecucionIa.class);
        verify(ejecuciones).save(captor.capture());

        String envio = captor.getValue().getEnvio();
        /*
         * ⚠️ Los rótulos llevan una marca sorteada, y por eso aquí se buscan por patrón.
         *
         * Es de la bitácora, no del prompt: al modelo se le mandan `sistema` y `contenido`
         * por separado y esta cadena no viaja nunca, así que **la caché de DeepSeek no se
         * entera** — que es justo lo que esta clase cuida. La marca está para que un texto
         * de usuario que acabe dentro no pueda escribir su propia sección «INSTRUCCIÓN» y
         * dejar el registro sin forma de leerse.
         *
         * Lo que se comprueba sigue siendo lo mismo: el orden.
         */
        assertThat(envio).containsPattern("^=== INSTRUCCIÓN · [a-z0-9]+ ===");
        assertThat(envio.indexOf("=== DATOS · "))
                .as("los datos del candidato van después de la instrucción")
                .isGreaterThan(envio.indexOf("=== INSTRUCCIÓN · "));
        assertThat(envio.indexOf("Ana Perez"))
                .as("el currículum aparece solo en la parte de datos")
                .isGreaterThan(envio.indexOf("=== DATOS · "));
    }
}
