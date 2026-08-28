package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.service.EjecutorAgenteIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCuestionarioTecnico.BloquePedido;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCuestionarioTecnico.FichaDelDueno;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCuestionarioTecnico.InsumoRedactor;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCuestionarioTecnico.PreguntaGenerada;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCuestionarioTecnico.ResultadoRedactor;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteRedactor;
import com.renaser.ai.ai_engine.perfilintegral.service.RecetaCuestionarioTecnico;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El REDACTOR: la aduana manda sobre lo que el modelo devuelva, con una sola segunda
 * oportunidad. Un borrador que no cuadra jamás se guarda.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El agente REDACTOR")
class AgenteRedactorTest {

    @Mock private PuenteRedactor puente;
    @Mock private EjecutorAgenteIa ejecutor;

    @InjectMocks
    private AgenteRedactor agente;

    private final TrabajoIa trabajo = TrabajoIa.builder()
            .id(1L).agenteCodigo("REDACTOR").referenciaTabla("vacante").referenciaId(50L)
            .build();

    private static InsumoRedactor insumo() {
        return new InsumoRedactor("EJECUCION", "Cajero", "Caja de la sede centro",
                new FichaDelDueno("q1", "q2", "q3", "q4", "q5", "q6", "q7", "q8", "q9", null,
                        12, 0, "Caja", "Margen", "Inventario", "Personal",
                        "Caja", null, null, null, null, "F4"),
                List.of(new BloquePedido("EXPERIENCIA", 2, null)));
    }

    private static ResultadoRedactor valido() {
        List<PreguntaGenerada> preguntas = new ArrayList<>();
        int n = 1;
        for (BloquePedido bloque : RecetaCuestionarioTecnico.estructura("EJECUCION")) {
            for (int i = 0; i < bloque.cantidad(); i++) {
                preguntas.add(new PreguntaGenerada("T%02d".formatted(n++), bloque.bloque(),
                        null, "¿Qué controlabas y de qué monto?",
                        "montos", "el faltante", "respuesta genérica", false));
            }
        }
        return new ResultadoRedactor(preguntas);
    }

    private static ResultadoRedactor roto() {
        return new ResultadoRedactor(List.of(new PreguntaGenerada(
                "T01", "EXPERIENCIA", null, "¿Qué controlabas?", null, null, null, false)));
    }

    @Test
    @DisplayName("Un borrador que pasa la aduana se guarda con una sola llamada")
    void elBuenoALaPrimera() {
        when(puente.insumo(50L)).thenReturn(insumo());
        when(ejecutor.ejecutar(eq(trabajo), anyString(), anyString(), any(),
                eq(ResultadoRedactor.class)))
                .thenReturn(new EjecutorAgenteIa.Ejecutado<>(9L, valido()));

        agente.ejecutar(trabajo);

        verify(ejecutor, times(1)).ejecutar(eq(trabajo), anyString(), anyString(), any(),
                eq(ResultadoRedactor.class));
        verify(puente).guardarBorrador(eq(50L), any());
    }

    @Test
    @DisplayName("Al que no cuadra se le devuelven los errores UNA vez, y si corrige, se guarda")
    void laSegundaOportunidad() {
        when(puente.insumo(50L)).thenReturn(insumo());
        when(ejecutor.ejecutar(eq(trabajo), anyString(), anyString(), any(),
                eq(ResultadoRedactor.class)))
                .thenReturn(new EjecutorAgenteIa.Ejecutado<>(9L, roto()))
                .thenReturn(new EjecutorAgenteIa.Ejecutado<>(10L, valido()));

        agente.ejecutar(trabajo);

        // La segunda llamada lleva los errores de la primera pegados al formato.
        verify(ejecutor).ejecutar(eq(trabajo), anyString(),
                contains("tuvo estos errores"), any(), eq(ResultadoRedactor.class));
        verify(puente).guardarBorrador(eq(50L), any());
    }

    @Test
    @DisplayName("Si tampoco a la segunda cuadra, el trabajo falla y nada se guarda")
    void alaSegundaSeAcabo() {
        when(puente.insumo(50L)).thenReturn(insumo());
        when(ejecutor.ejecutar(eq(trabajo), anyString(), anyString(), any(),
                eq(ResultadoRedactor.class)))
                .thenReturn(new EjecutorAgenteIa.Ejecutado<>(9L, roto()))
                .thenReturn(new EjecutorAgenteIa.Ejecutado<>(10L, roto()));

        assertThatThrownBy(() -> agente.ejecutar(trabajo))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no pasó la aduana");
        verify(puente, never()).guardarBorrador(any(), any());
    }

    @Test
    @DisplayName("Un modelo que no devuelve preguntas es un error, no un cuestionario vacío")
    void sinPreguntasEsError() {
        when(puente.insumo(50L)).thenReturn(insumo());
        when(ejecutor.ejecutar(eq(trabajo), anyString(), anyString(), any(),
                eq(ResultadoRedactor.class)))
                .thenReturn(new EjecutorAgenteIa.Ejecutado<>(9L, new ResultadoRedactor(null)))
                .thenReturn(new EjecutorAgenteIa.Ejecutado<>(10L, new ResultadoRedactor(null)));

        assertThatThrownBy(() -> agente.ejecutar(trabajo))
                .isInstanceOf(IllegalStateException.class);
        verify(puente, never()).guardarBorrador(any(), any());
    }

    @Test
    @DisplayName("Su código es REDACTOR: el que la cola usa para despacharle trabajos")
    void suCodigo() {
        assertThat(agente.codigo()).isEqualTo("REDACTOR");
    }
}
