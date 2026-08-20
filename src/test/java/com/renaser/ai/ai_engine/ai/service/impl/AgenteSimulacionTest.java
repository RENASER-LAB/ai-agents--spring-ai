package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.service.EjecutorAgenteIa;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacionIa.Contradiccion;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacionIa.InsumoConversacion;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacionIa.PreguntaIa;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacionIa.ResultadoConversacion;
import com.renaser.ai.ai_engine.simulacion.service.PuenteSimulacionIa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El agente que prepara las preguntas de la conversación final.
 *
 * <p>Dos cosas se sostienen aquí. La primera es que <b>siempre razona</b>: encontrar el hueco
 * entre lo que alguien dijo y lo que se le vio hacer es justo lo que el modelo hace cuando
 * delibera, y aquí no hay tanda de cien currículums que ordenar deprisa —son los tres o
 * cuatro candidatos que llegaron a la simulación—.
 *
 * <p>La segunda es que <b>un fallo sube</b>. Si el puente dice que todavía no hay nada de lo
 * que preguntar, el agente no lo disimula con una lista vacía: lanza, y la cola decide si
 * reintenta. Tragárselo dejaría al facilitador con una conversación final sin preguntas y
 * sin ninguna señal de por qué.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El agente que prepara la conversación final")
class AgenteSimulacionTest {

    private static final long POSTULACION = 55L;

    @Mock private PuenteSimulacionIa puente;
    @Mock private EjecutorAgenteIa ejecutor;

    @InjectMocks
    private AgenteSimulacion agente;

    @Test
    void siempreRazona() {
        ResultadoConversacion resultado = new ResultadoConversacion(List.of(
                new PreguntaIa("Lo viste a las 10:41 y lo dijiste a las 10:49, ¿qué pasó?",
                        "Ocho minutos entre ver el bloqueo y avisarlo", 9L)));
        when(puente.insumoConversacion(POSTULACION)).thenReturn(insumo());
        when(ejecutor.ejecutar(any(TrabajoIa.class), anyString(), anyString(), any(),
                eq(ResultadoConversacion.class), eq(true)))
                .thenReturn(new EjecutorAgenteIa.Ejecutado<>(77L, resultado));

        agente.ejecutar(trabajo());

        verify(puente).guardarPreguntas(POSTULACION, 77L, resultado);
    }

    @Test
    void siNoHayNadaDeLoQuePreguntarElFalloSube() {
        when(puente.insumoConversacion(POSTULACION))
                .thenThrow(new IllegalStateException("no tiene todavía nada de lo que preguntar"));

        assertThatThrownBy(() -> agente.ejecutar(trabajo()))
                .isInstanceOf(IllegalStateException.class);

        verify(puente, never()).guardarPreguntas(any(), any(), any());
    }

    @Test
    void elFormatoPideElHechoDelQueSaleCadaPregunta() {
        // Sin el motivo, el facilitador recibe una pregunta y no sabe por qué se la dan, así
        // que tampoco sabe repreguntar cuando la respuesta se va por las ramas.
        assertThat(AgenteSimulacion.FORMATO).contains("motivo");
        assertThat(AgenteSimulacion.FORMATO).contains("alertaId");
        assertThat(AgenteSimulacion.FORMATO).contains("json");
    }

    // ============ Apoyo ============

    private InsumoConversacion insumo() {
        return new InsumoConversacion("Analista de procesos", "OPERATIVO", "Se busca...",
                "Perfil sólido en ejecución",
                List.of(), List.of(new Contradiccion(9L, "CONTRADICCION", "Dice que avisa pronto")),
                List.of(), List.of(), List.of(), List.of());
    }

    private TrabajoIa trabajo() {
        return TrabajoIa.builder()
                .id(1L)
                .postulacionId(POSTULACION)
                .organizacionId(1L)
                .agenteCodigo(AgenteSimulacion.CODIGO)
                .modo("FINA")
                .estado("EN_CURSO")
                .creadoEn(Instant.now())
                .build();
    }
}
