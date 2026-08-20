package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.service.EjecutorAgenteIa;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.CriterioDeRubrica;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.InsumoPrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.NotaCriterioPruebaIa;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.ResultadoPrueba;
import com.renaser.ai.ai_engine.prueba.service.PuentePruebaIa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El agente que califica la prueba del puesto: cuándo llama al modelo y cuándo no.
 *
 * <p>Lo que se prueba aquí es de dinero, no de estilo. Una llamada al modelo se paga, y hay
 * un caso en que gastarla no sirve para nada: cuando la rúbrica de esa prueba no le reserva
 * ni un criterio al agente. Ocurre de verdad —una prueba que se califica mirando un video se
 * marca entera como de persona— y si el agente no lo mirara antes, cada entrega de esa
 * prueba pagaría una petición para recibir una lista vacía.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El agente que califica la prueba del puesto")
class AgentePruebaPuestoTest {

    private static final long POSTULACION = 55L;

    @Mock private PuentePruebaIa puente;
    @Mock private EjecutorAgenteIa ejecutor;

    @InjectMocks
    private AgentePruebaPuesto agente;

    @Test
    void sinNingunCriterioParaElAgenteNoSeLlamaAlModelo() {
        when(puente.insumoPrueba(POSTULACION)).thenReturn(insumo(List.of()));

        agente.ejecutar(trabajo());

        verifyNoInteractions(ejecutor);
        verify(puente, never()).guardarNotasPrueba(any(), any(), any());
    }

    @Test
    void loQueDevuelveElModeloSeGuardaSelladoConSuEjecucion() {
        // El id de la ejecución es lo que permite abrir una nota de hace seis meses y ver
        // exactamente qué se le mandó al modelo y qué contestó (RF-146). Una nota de agente
        // sin ese sello es una nota que nadie puede auditar.
        InsumoPrueba insumo = insumo(List.of(
                new CriterioDeRubrica("PR_CALIDAD", "Calidad", "Qué tan bien hecho está",
                        BigDecimal.valueOf(20))));
        ResultadoPrueba resultado = new ResultadoPrueba(
                List.of(new NotaCriterioPruebaIa("PR_CALIDAD", BigDecimal.valueOf(16),
                        "Resolvió lo pedido", "La hoja de cálculo cuadra")),
                BigDecimal.valueOf(80));
        when(puente.insumoPrueba(POSTULACION)).thenReturn(insumo);
        when(ejecutor.ejecutar(any(TrabajoIa.class), anyString(), anyString(), eq(insumo),
                eq(ResultadoPrueba.class)))
                .thenReturn(new EjecutorAgenteIa.Ejecutado<>(77L, resultado));

        agente.ejecutar(trabajo());

        verify(puente).guardarNotasPrueba(POSTULACION, 77L, resultado);
    }

    @Test
    void elFormatoLeDiceQueElMaximoLoTraeCadaCriterio() {
        // La diferencia con el evaluador del hito 2, donde todo iba de 0 a 4. Aquí cada
        // criterio vale lo que diga su rúbrica, y si el formato no lo dijera el modelo
        // devolvería notas sobre 100 que después habría que reescalar a ojo.
        assertThat(AgentePruebaPuesto.FORMATO).contains("puntosMaximos");
        assertThat(AgentePruebaPuesto.FORMATO).contains("json");
    }

    // ============ Apoyo ============

    private InsumoPrueba insumo(List<CriterioDeRubrica> criterios) {
        return new InsumoPrueba("Analista de procesos", "OPERATIVO", "Se busca...",
                "Arma el tablero", null, null, 120, null, false,
                criterios, List.of(), List.of());
    }

    private TrabajoIa trabajo() {
        return TrabajoIa.builder()
                .id(1L)
                .postulacionId(POSTULACION)
                .organizacionId(1L)
                .agenteCodigo(AgentePruebaPuesto.CODIGO)
                .modo("FINA")
                .estado("EN_CURSO")
                .creadoEn(Instant.now())
                .build();
    }
}
