package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.model.TrabajoIa;
import com.renaser.ai.ai_engine.ai.service.EjecutorAgenteIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.CasoPropuesto;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.CriterioPropuesto;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.EntregablePropuesto;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.InsumoRecomendadorPrueba;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.PreguntaPropuesta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.ResultadoRecomendadorPrueba;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteRecomendador;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.AbiertaParaLaIa;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.CriterioParaLaIa;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.InsumoPruebaPropia;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.NotaCriterioPropiaIa;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.ResultadoPruebaPropia;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaIa.TandaCalificada;
import com.renaser.ai.ai_engine.prueba.service.PuentePruebaIa;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Los dos agentes cuando trabajan sobre la prueba técnica escrita en el editor (V67): el que
 * la califica, en varias llamadas si hace falta y guardando todo junto; y el que la
 * recomienda, con su aduana y una sola segunda oportunidad.
 */
@DisplayName("Los agentes de la prueba escrita en el editor")
class AgentesDeLaPruebaDelEditorTest {

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("El agente que califica")
    class ElQueCalifica {

        @Mock private PuentePruebaIa puente;
        @Mock private EjecutorAgenteIa ejecutor;
        @InjectMocks private AgentePruebaPuesto agente;

        private final TrabajoIa trabajo = TrabajoIa.builder().id(9L).postulacionId(70L)
                .agenteCodigo("PRUEBA_PUESTO").modo("FINA").build();

        /** Doce criterios con cinco abiertas cada uno: sesenta preguntas. */
        private InsumoPruebaPropia insumoGrande() {
            List<CriterioParaLaIa> criterios = IntStream.rangeClosed(1, 12)
                    .mapToObj(i -> new CriterioParaLaIa((long) i, "Criterio " + i, "Evalúa " + i, 8,
                            IntStream.rangeClosed(1, 5).mapToObj(j -> new AbiertaParaLaIa(
                                    "Pregunta " + i + "." + j, "Lo que debe tener", "Mi respuesta")).toList(),
                            List.of()))
                    .toList();
            return new InsumoPruebaPropia(null, "El caso", null, null, "CRONOMETRADA", 90, null,
                    false, "La guía", 3, criterios);
        }

        @Test
        @DisplayName("Una prueba de 60 preguntas y 12 criterios se califica en varias llamadas y se guarda entera (AC-29)")
        void variasLlamadasYUnSoloGuardado() {
            when(puente.esDelEditor(70L)).thenReturn(true);
            when(puente.insumoPruebaPropia(70L)).thenReturn(insumoGrande());
            List<InsumoPruebaPropia> enviados = new ArrayList<>();
            when(ejecutor.ejecutar(any(TrabajoIa.class), anyString(), anyString(), any(),
                    eq(ResultadoPruebaPropia.class))).thenAnswer(inv -> {
                        InsumoPruebaPropia tanda = inv.getArgument(3);
                        enviados.add(tanda);
                        return new EjecutorAgenteIa.Ejecutado<>((long) enviados.size(),
                                new ResultadoPruebaPropia(tanda.criterios().stream()
                                        .map(c -> new NotaCriterioPropiaIa(c.criterioId(),
                                                BigDecimal.valueOf(5), "Bien", "cita"))
                                        .toList(), BigDecimal.TEN));
                    });

            agente.ejecutar(trabajo);

            assertThat(enviados).hasSizeGreaterThan(1);
            assertThat(enviados).allMatch(t -> t.criterios().size() <= AgentePruebaPuesto.CRITERIOS_POR_LLAMADA);
            // La guía no viaja en los datos: va envuelta en el system
            assertThat(enviados).allMatch(t -> t.guiaCalificacion() == null);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<TandaCalificada>> tandas = ArgumentCaptor.forClass(List.class);
            verify(puente).guardarNotasPruebaPropia(eq(70L), eq(3), tandas.capture());
            List<Long> calificados = tandas.getValue().stream()
                    .flatMap(t -> t.resultado().criterios().stream())
                    .map(NotaCriterioPropiaIa::criterioId).toList();
            assertThat(calificados).containsExactlyInAnyOrderElementsOf(
                    IntStream.rangeClosed(1, 12).mapToObj(i -> (long) i).toList());
        }

        @Test
        @DisplayName("Sin criterios de IA por calificar no llama al modelo")
        void sinCriteriosNoLlama() {
            when(puente.esDelEditor(70L)).thenReturn(true);
            when(puente.insumoPruebaPropia(70L)).thenReturn(new InsumoPruebaPropia(null, null, null,
                    null, "CRONOMETRADA", 30, null, false, null, 1, List.of()));

            agente.ejecutar(trabajo);

            verify(ejecutor, never()).ejecutar(any(TrabajoIa.class), anyString(), anyString(), any(), any());
            verify(puente, never()).guardarNotasPruebaPropia(any(), any(int.class), anyList());
        }

        @Test
        @DisplayName("Si una llamada falla, no se guarda nada: todo o nada para la persona")
        void siUnaFallaNoSeGuardaNada() {
            when(puente.esDelEditor(70L)).thenReturn(true);
            when(puente.insumoPruebaPropia(70L)).thenReturn(insumoGrande());
            when(ejecutor.ejecutar(any(TrabajoIa.class), anyString(), anyString(), any(),
                    eq(ResultadoPruebaPropia.class)))
                    .thenReturn(new EjecutorAgenteIa.Ejecutado<>(1L,
                            new ResultadoPruebaPropia(List.of(), BigDecimal.TEN)))
                    .thenThrow(new IllegalStateException("el modelo no respondió"));

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> agente.ejecutar(trabajo))
                    .isInstanceOf(IllegalStateException.class);
            verify(puente, never()).guardarNotasPruebaPropia(any(), any(int.class), anyList());
        }

        @Test
        @DisplayName("Las tandas respetan el tope de texto aunque sean pocos criterios")
        void lasTandasRespetanElTexto() {
            String largo = "x".repeat(AgentePruebaPuesto.CARACTERES_POR_LLAMADA / 2 + 10);
            List<CriterioParaLaIa> criterios = IntStream.rangeClosed(1, 3)
                    .mapToObj(i -> new CriterioParaLaIa((long) i, "C" + i, null, 10,
                            List.of(new AbiertaParaLaIa("P", null, largo)), List.of()))
                    .toList();
            assertThat(AgentePruebaPuesto.enTandas(criterios)).hasSize(3);
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("El agente que recomienda")
    class ElQueRecomienda {

        private static final long VACANTE = 41L;

        @Mock private PuenteRecomendador puente;
        @Mock private EjecutorAgenteIa ejecutor;
        @InjectMocks private AgenteRecomendador agente;

        private final TrabajoIa trabajo = TrabajoIa.builder().id(2L).agenteCodigo("RECOMENDADOR")
                .referenciaTabla(ColaCalificacionIaImpl.REFERENCIA_PRUEBA).referenciaId(VACANTE)
                .modo("FINA").build();
        private final InsumoRecomendadorPrueba insumo = new InsumoRecomendadorPrueba(null, 100, null,
                List.of("ABIERTA"), null, "CRONOMETRADA", 60, null, List.of(), List.of());

        /** Un criterio de IA que solo mira un enlace, sin abiertas: no vale. */
        private static ResultadoRecomendadorPrueba soloEnlaceDeIa() {
            return new ResultadoRecomendadorPrueba(new CasoPropuesto("Un caso", null, null),
                    List.of(new EntregablePropuesto("Video", "Dos minutos", "ENLACE", true, null)),
                    List.of(new CriterioPropuesto(null, "Comunicación", null, List.of(),
                            null, "IA", List.of(0), List.of(), new BigDecimal("100"))));
        }

        private static ResultadoRecomendadorPrueba deUnaPersona() {
            return new ResultadoRecomendadorPrueba(new CasoPropuesto("Un caso", null, null),
                    List.of(new EntregablePropuesto("Video", "Dos minutos", "ENLACE", true, null)),
                    List.of(new CriterioPropuesto(null, "Comunicación", null,
                            List.of(new PreguntaPropuesta("OPCION_UNICA", "¿Qué dirías primero?",
                                    new BigDecimal("10"), null, List.of(
                                    new com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.OpcionPropuesta("El dato", new BigDecimal("10")),
                                    new com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.OpcionPropuesta("Un saludo", BigDecimal.ZERO)))),
                            null, "PERSONA", List.of(0), List.of(), new BigDecimal("100"))));
        }

        @Test
        @DisplayName("Un criterio de IA que solo mira un enlace, dos veces seguidas: la generación queda fallida (AC-21)")
        void dosVecesSoloEnlaceQuedaFallida() {
            when(puente.insumoPrueba(VACANTE)).thenReturn(insumo);
            when(ejecutor.ejecutar(any(TrabajoIa.class), anyString(), anyString(), any(),
                    eq(ResultadoRecomendadorPrueba.class)))
                    .thenReturn(new EjecutorAgenteIa.Ejecutado<>(1L, soloEnlaceDeIa()));

            agente.ejecutar(trabajo);

            verify(ejecutor, times(2)).ejecutar(any(TrabajoIa.class), anyString(), anyString(), any(),
                    eq(ResultadoRecomendadorPrueba.class));
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<String>> errores = ArgumentCaptor.forClass(List.class);
            verify(puente).marcarFallidaPrueba(eq(VACANTE), errores.capture());
            assertThat(errores.getValue()).anyMatch(e -> e.contains("la IA no abre enlaces"));
            verify(puente, never()).guardarPropuestaPrueba(any(), any());
            // Y nunca toca el carril de las preguntas del Perfil Integral
            verify(puente, never()).insumo(any());
        }

        @Test
        @DisplayName("Un entregable sin nadie que lo califique, dos veces seguidas: la generación queda fallida (AC-21)")
        void dosVecesSinQuienLoCalifiqueQuedaFallida() {
            var cerrada = new PreguntaPropuesta("OPCION_UNICA", "¿Qué libro?", new BigDecimal("100"), null,
                    List.of(new com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.OpcionPropuesta("Diario", new BigDecimal("100")),
                            new com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.OpcionPropuesta("Caja", BigDecimal.ZERO)));
            var sinQuien = new ResultadoRecomendadorPrueba(null,
                    List.of(new EntregablePropuesto("Hoja.xlsx", null, "ARCHIVO", true, null,
                            new com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.PosicionDePregunta(0, 0),
                            null, null)),
                    List.of(new CriterioPropuesto(null, "Solo cerradas", null, List.of(cerrada),
                            null, null, null, null, new BigDecimal("100"))));
            when(puente.insumoPrueba(VACANTE)).thenReturn(insumo);
            when(ejecutor.ejecutar(any(TrabajoIa.class), anyString(), anyString(), any(),
                    eq(ResultadoRecomendadorPrueba.class)))
                    .thenReturn(new EjecutorAgenteIa.Ejecutado<>(1L, sinQuien));

            agente.ejecutar(trabajo);

            verify(ejecutor, times(2)).ejecutar(any(TrabajoIa.class), anyString(), anyString(), any(),
                    eq(ResultadoRecomendadorPrueba.class));
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<String>> errores = ArgumentCaptor.forClass(List.class);
            verify(puente).marcarFallidaPrueba(eq(VACANTE), errores.capture());
            assertThat(errores.getValue()).anyMatch(e -> e.contains("no lo califica nadie"));
            verify(puente, never()).guardarPropuestaPrueba(any(), any());
        }

        @Test
        @DisplayName("Si la corrección cuadra, se guarda la propuesta de la prueba")
        void laCorreccionSeGuarda() {
            when(puente.insumoPrueba(VACANTE)).thenReturn(insumo);
            when(ejecutor.ejecutar(any(TrabajoIa.class), anyString(), anyString(), any(),
                    eq(ResultadoRecomendadorPrueba.class)))
                    .thenReturn(new EjecutorAgenteIa.Ejecutado<>(1L, soloEnlaceDeIa()),
                            new EjecutorAgenteIa.Ejecutado<>(2L, deUnaPersona()));

            agente.ejecutar(trabajo);

            verify(puente).guardarPropuestaPrueba(eq(VACANTE), any());
            verify(puente, never()).marcarFallidaPrueba(any(), anyList());
        }

        @Test
        @DisplayName("Un criterio cuyas cerradas pasan de lo que vale, dos veces seguidas: la generación queda fallida (V69)")
        void dosVecesCerradasPorEncimaQuedaFallida() {
            var porEncima = new ResultadoRecomendadorPrueba(null, List.of(),
                    List.of(new CriterioPropuesto(null, "Comunicación", null,
                            List.of(new PreguntaPropuesta("OPCION_UNICA", "¿Qué dirías primero?",
                                    new BigDecimal("100"), null, List.of(
                                    new com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.OpcionPropuesta("El dato", new BigDecimal("100")),
                                    new com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.OpcionPropuesta("Un saludo", BigDecimal.ZERO)))),
                            null, null, null, null, new BigDecimal("60"))));
            when(puente.insumoPrueba(VACANTE)).thenReturn(insumo);
            when(ejecutor.ejecutar(any(TrabajoIa.class), anyString(), anyString(), any(),
                    eq(ResultadoRecomendadorPrueba.class)))
                    .thenReturn(new EjecutorAgenteIa.Ejecutado<>(1L, porEncima));

            agente.ejecutar(trabajo);

            verify(ejecutor, times(2)).ejecutar(any(TrabajoIa.class), anyString(), anyString(), any(),
                    eq(ResultadoRecomendadorPrueba.class));
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<String>> errores = ArgumentCaptor.forClass(List.class);
            verify(puente).marcarFallidaPrueba(eq(VACANTE), errores.capture());
            assertThat(errores.getValue()).anyMatch(e -> e.contains("sus cerradas suman 100 y el criterio vale 60"));
            verify(puente, never()).guardarPropuestaPrueba(any(), any());
        }
    }
}
