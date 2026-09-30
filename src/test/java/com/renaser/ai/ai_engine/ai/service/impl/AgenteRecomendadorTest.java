package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.service.EjecutorAgenteIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.CriterioPropuesto;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.InsumoRecomendador;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.PreguntaPropuesta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.ResultadoRecomendador;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteRecomendador;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("El agente que recomienda preguntas propias")
class AgenteRecomendadorTest {

    private static final long VACANTE = 40L;

    @Mock private PuenteRecomendador puente;
    @Mock private EjecutorAgenteIa ejecutor;
    @InjectMocks private AgenteRecomendador agente;

    private final TrabajoIa trabajo = TrabajoIa.builder().id(1L).agenteCodigo("RECOMENDADOR")
            .referenciaTabla("vacante").referenciaId(VACANTE).modo("FINA").build();
    private final InsumoRecomendador insumo = new InsumoRecomendador(null, 100, null,
            List.of("ABIERTA"), List.of(), List.of());

    private static ResultadoRecomendador sumando(int puntos) {
        return new ResultadoRecomendador(List.of(new CriterioPropuesto(null, "Contabilidad", null,
                List.of(new PreguntaPropuesta("ABIERTA", "¿Cómo cierras el mes?",
                        BigDecimal.valueOf(puntos), null, List.of())))));
    }

    @Test
    @DisplayName("Si no pasa la aduana dos veces seguidas, queda fallida y no se agrega nada (AC-14)")
    void dosVecesMalQuedaFallida() {
        when(puente.insumo(VACANTE)).thenReturn(insumo);
        when(ejecutor.ejecutar(any(TrabajoIa.class), anyString(), anyString(), any(),
                eq(ResultadoRecomendador.class)))
                .thenReturn(new EjecutorAgenteIa.Ejecutado<>(1L, sumando(80)));

        agente.ejecutar(trabajo);

        verify(ejecutor, times(2)).ejecutar(any(TrabajoIa.class), anyString(), anyString(), any(),
                eq(ResultadoRecomendador.class));
        verify(puente).marcarFallida(eq(VACANTE), anyList());
        verify(puente, never()).guardarPropuesta(any(), any());
    }

    @Test
    @DisplayName("Si la corrección cuadra, se guarda la propuesta; el borrador no se toca")
    void laCorreccionSeGuarda() {
        when(puente.insumo(VACANTE)).thenReturn(insumo);
        when(ejecutor.ejecutar(any(TrabajoIa.class), anyString(), anyString(), any(),
                eq(ResultadoRecomendador.class)))
                .thenReturn(new EjecutorAgenteIa.Ejecutado<>(1L, sumando(80)),
                        new EjecutorAgenteIa.Ejecutado<>(2L, sumando(100)));

        agente.ejecutar(trabajo);

        verify(puente).guardarPropuesta(eq(VACANTE), any());
        verify(puente, never()).marcarFallida(any(), anyList());
    }

    @Test
    @DisplayName("Sin propuesta pedida, no llama al modelo")
    void sinPedidaNoLlama() {
        when(puente.insumo(VACANTE)).thenReturn(null);

        agente.ejecutar(trabajo);

        verify(ejecutor, never()).ejecutar(any(TrabajoIa.class), anyString(), anyString(), any(),
                eq(ResultadoRecomendador.class));
    }
}
