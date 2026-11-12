package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.service.EjecutorAgenteIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.InsumoBusquedaCv;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoDeLaBusquedaIa;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteBusquedaCv;
import com.renaser.ai.ai_engine.postulacion.service.CvSinTexto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("El agente de «Qué buscar en el CV»")
class AgenteBusquedaCvTest {

    private static final long POSTULACION = 40L;

    @Mock private PuenteBusquedaCv puente;
    @Mock private EjecutorAgenteIa ejecutor;

    private AgenteBusquedaCv agente;
    private final TrabajoIa trabajo = TrabajoIa.builder().id(1L).postulacionId(POSTULACION)
            .agenteCodigo(AgenteBusquedaCv.CODIGO_AGENTE).modo("FINA").build();

    @BeforeEach
    void armar() {
        agente = new AgenteBusquedaCv(puente, ejecutor, true);
    }

    private static InsumoBusquedaCv insumo(int version) {
        return new InsumoBusquedaCv("Chef de cocina", "Bajé la merma del 9 % al 4 %.",
                List.of("Controla costos y mermas", "Certificado BPM"), version);
    }

    @Test
    @DisplayName("AC-11: con el CV sin texto se marca al primer intento, sin llamar a la IA")
    void cvSinTextoNoLlamaALaIa() {
        when(puente.insumo(POSTULACION)).thenThrow(new CvSinTexto("El PDF no tiene texto"));

        agente.ejecutar(trabajo);

        verify(puente).marcarSinTexto(POSTULACION, "El PDF no tiene texto");
        verify(ejecutor, never()).ejecutar(any(), anyString(), anyString(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("Sin líneas no hace nada")
    void sinLineasNoHaceNada() {
        agente.ejecutar(trabajo);

        verify(ejecutor, never()).ejecutar(any(), anyString(), anyString(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("Todo va en los datos: las líneas numeradas y envueltas, el puesto y el CV")
    void todoEnLosDatos() {
        when(puente.insumo(POSTULACION)).thenReturn(insumo(3));
        when(ejecutor.ejecutar(eq(trabajo), anyString(), eq(AgenteBusquedaCv.FORMATO), any(),
                eq(ResultadoDeLaBusquedaIa.class), eq(true)))
                .thenReturn(new EjecutorAgenteIa.Ejecutado<>(9L, new ResultadoDeLaBusquedaIa(List.of())));
        when(puente.guardar(eq(POSTULACION), eq(9L), any(), eq(3), eq(false))).thenReturn(true);

        agente.ejecutar(trabajo);

        ArgumentCaptor<Object> datos = ArgumentCaptor.forClass(Object.class);
        verify(ejecutor).ejecutar(eq(trabajo), anyString(), eq(AgenteBusquedaCv.FORMATO),
                datos.capture(), eq(ResultadoDeLaBusquedaIa.class), eq(true));
        AgenteBusquedaCv.DatosDeLaBusqueda enviados = (AgenteBusquedaCv.DatosDeLaBusqueda) datos.getValue();
        assertThat(enviados.puesto()).isEqualTo("Chef de cocina");
        assertThat(enviados.curriculum()).contains("merma");
        assertThat(enviados.queBuscarEnElCv())
                .contains("1. Controla costos y mermas\n2. Certificado BPM")
                .contains("LO QUE LA EMPRESA BUSCA EN EL CV")
                .contains("FIN DE LO QUE LA EMPRESA BUSCA");
    }

    @Test
    @DisplayName("Si las líneas cambian mientras busca, vuelve a buscar con las nuevas")
    void siCambianVuelveABuscar() {
        when(puente.insumo(POSTULACION)).thenReturn(insumo(3), insumo(4));
        when(ejecutor.ejecutar(any(), anyString(), anyString(), any(), eq(ResultadoDeLaBusquedaIa.class),
                anyBoolean()))
                .thenReturn(new EjecutorAgenteIa.Ejecutado<>(9L, new ResultadoDeLaBusquedaIa(List.of())));
        when(puente.guardar(eq(POSTULACION), anyLong(), any(), eq(3), anyBoolean())).thenReturn(false);
        when(puente.guardar(eq(POSTULACION), anyLong(), any(), eq(4), anyBoolean())).thenReturn(true);

        agente.ejecutar(trabajo);

        verify(puente, times(2)).insumo(POSTULACION);
    }

    @Test
    @DisplayName("Tres cambios seguidos: falla el intento y la cola lo reintenta más tarde")
    void tresCambiosFallan() {
        when(puente.insumo(POSTULACION)).thenReturn(insumo(3));
        when(ejecutor.ejecutar(any(), anyString(), anyString(), any(), eq(ResultadoDeLaBusquedaIa.class),
                anyBoolean()))
                .thenReturn(new EjecutorAgenteIa.Ejecutado<>(9L, new ResultadoDeLaBusquedaIa(List.of())));
        when(puente.guardar(eq(POSTULACION), anyLong(), any(), anyInt(), anyBoolean())).thenReturn(false);

        assertThatThrownBy(() -> agente.ejecutar(trabajo)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("En una rebúsqueda guarda como rebúsqueda")
    void rebusqueda() {
        TrabajoIa rebusca = TrabajoIa.builder().id(2L).postulacionId(POSTULACION)
                .agenteCodigo(AgenteBusquedaCv.CODIGO_AGENTE).modo("REBUSCA").build();
        when(puente.insumo(POSTULACION)).thenReturn(insumo(3));
        when(ejecutor.ejecutar(any(), anyString(), anyString(), any(), eq(ResultadoDeLaBusquedaIa.class),
                anyBoolean()))
                .thenReturn(new EjecutorAgenteIa.Ejecutado<>(9L, new ResultadoDeLaBusquedaIa(List.of())));
        when(puente.guardar(POSTULACION, 9L, new ResultadoDeLaBusquedaIa(List.of()), 3, true))
                .thenReturn(true);

        agente.ejecutar(rebusca);

        verify(puente).guardar(POSTULACION, 9L, new ResultadoDeLaBusquedaIa(List.of()), 3, true);
    }
}
