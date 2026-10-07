package com.renaser.ai.ai_engine.prueba.service;

import com.renaser.ai.ai_engine.perfilintegral.entity.CriterioBanco;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.OpcionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.prueba.entity.CriterioBancoEntregable;
import com.renaser.ai.ai_engine.prueba.entity.EntregableCubrePregunta;
import com.renaser.ai.ai_engine.prueba.entity.EntregableRequerido;
import com.renaser.ai.ai_engine.prueba.repository.CriterioBancoEntregableRepository;
import com.renaser.ai.ai_engine.prueba.repository.EntregableCubrePreguntaRepository;
import com.renaser.ai.ai_engine.prueba.repository.EntregableRepository;
import com.renaser.ai.ai_engine.prueba.repository.EntregableRequeridoRepository;
import com.renaser.ai.ai_engine.prueba.repository.NotaCriterioPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.RespuestaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia.CriterioDeLaPrueba;
import com.renaser.ai.ai_engine.prueba.service.CalificacionDeLaPruebaPropia.Resultado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * «Mira» lo deduce el sistema (V68, punto 8): un criterio mira el archivo de cada una de sus
 * preguntas y los entregables generales que cubren toda la prueba o alguna de sus preguntas.
 * Las versiones de antes conservan lo marcado a mano (AC-22).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Lo que mira cada criterio, deducido del alcance")
class MiraDeducidaTest {

    private static final long VERSION = 80L;

    @Mock private VersionBancoRepository versionesBanco;
    @Mock private CriterioBancoRepository criteriosBanco;
    @Mock private PreguntaRepository preguntas;
    @Mock private OpcionRepository opciones;
    @Mock private EntregableRequeridoRepository entregables;
    @Mock private CriterioBancoEntregableRepository miradas;
    @Mock private RespuestaPruebaRepository respuestas;
    @Mock private NotaCriterioPruebaRepository notas;
    @Mock private EntregableRepository subidas;
    @Mock private EntregableCubrePreguntaRepository cubiertas;
    @InjectMocks private CalificacionDeLaPruebaPropia calculo;

    private static CriterioBanco criterio(long id, int orden) {
        return CriterioBanco.builder().id(id).versionBancoId(VERSION).nombre("C" + id).orden(orden)
                .puntosCalificados(20).calificador("IA").build();
    }

    private static Pregunta abierta(long id, long criterio) {
        return Pregunta.builder().id(id).versionBancoId(VERSION).criterioBancoId(criterio)
                .orden((int) id).tipo("ABIERTA").enunciado("P" + id).puntos(0).build();
    }

    private static EntregableRequerido entregable(long id, String alcance, Long pregunta) {
        return EntregableRequerido.builder().id(id).versionBancoId(VERSION).nombre("E" + id)
                .formato("ARCHIVO").esObligatorio(true).orden((int) id).alcance(alcance)
                .preguntaId(pregunta).build();
    }

    @BeforeEach
    void armar() {
        when(versionesBanco.findById(VERSION)).thenReturn(Optional.of(
                VersionBanco.builder().id(VERSION).build()));
        when(criteriosBanco.findByVersionBancoIdIn(anyList()))
                .thenReturn(List.of(criterio(10L, 1), criterio(11L, 2), criterio(12L, 3)));
        // La 2 es del criterio 10 y la 4 del 11; el 12 no tiene preguntas.
        when(preguntas.findByVersionBancoIdIn(anyList())).thenReturn(List.of(
                abierta(2L, 10L), abierta(4L, 11L)));
    }

    private static List<Long> loQueMira(Resultado r, long criterio) {
        return r.criterios().stream().filter(c -> c.criterio().getId() == criterio).findFirst()
                .map(CriterioDeLaPrueba::entregables).orElseThrow().stream()
                .map(EntregableRequerido::getId).toList();
    }

    @Test
    @DisplayName("El archivo de una pregunta lo mira solo el criterio de esa pregunta (AC-04)")
    void elArchivoDeUnaPregunta() {
        when(entregables.findByVersionBancoIdIn(any())).thenReturn(List.of(
                entregable(30L, EntregableRequerido.PREGUNTA, 4L)));

        Resultado r = calculo.estructura(VERSION);

        assertThat(loQueMira(r, 10L)).isEmpty();
        assertThat(loQueMira(r, 11L)).containsExactly(30L);
        assertThat(loQueMira(r, 12L)).isEmpty();
    }

    @Test
    @DisplayName("Un general que cubre la 2 y la 4 lo miran sus dos criterios; uno de toda la prueba, todos (AC-05)")
    void losGenerales() {
        when(entregables.findByVersionBancoIdIn(any())).thenReturn(List.of(
                entregable(31L, EntregableRequerido.PREGUNTAS, null),
                entregable(32L, EntregableRequerido.TODA_LA_PRUEBA, null)));
        when(cubiertas.findByEntregableRequeridoIdIn(Set.of(31L))).thenReturn(List.of(
                EntregableCubrePregunta.builder().entregableRequeridoId(31L).preguntaId(2L).build(),
                EntregableCubrePregunta.builder().entregableRequeridoId(31L).preguntaId(4L).build()));

        Resultado r = calculo.estructura(VERSION);

        assertThat(loQueMira(r, 10L)).containsExactly(31L, 32L);
        assertThat(loQueMira(r, 11L)).containsExactly(31L, 32L);
        assertThat(loQueMira(r, 12L)).containsExactly(32L);
        assertThat(r.cubreDe(31L)).containsExactly(2L, 4L);
        assertThat(r.cubreDe(32L)).isEmpty();
    }

    @Test
    @DisplayName("Un entregable de antes, sin alcance, conserva lo marcado a mano (AC-22)")
    void loDeAntesSeConserva() {
        when(entregables.findByVersionBancoIdIn(any())).thenReturn(List.of(entregable(33L, null, null)));
        when(miradas.findByCriterioBancoIdIn(any())).thenReturn(List.of(
                CriterioBancoEntregable.builder().criterioBancoId(12L).entregableRequeridoId(33L).build()));

        Resultado r = calculo.estructura(VERSION);

        assertThat(loQueMira(r, 10L)).isEmpty();
        assertThat(loQueMira(r, 12L)).containsExactly(33L);
    }
}
