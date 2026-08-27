package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.NotaRespuestaIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoEvaluador;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaRespuesta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Respuesta;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaRespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaDimensionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.RespuestaRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El guardado de notas del EVALUADOR cuando el banco es CRITERIOS.
 *
 * <p>Las tres reglas que este camino no puede romper: el puntaje lo cuenta el código a
 * partir de los criterios (con la regla dura como dato), media tanda no es una nota —se
 * revienta para que la cola reintente—, y lo que una persona ajustó no se pisa pero sí
 * cuenta como calificado.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El guardado de notas por criterios")
class PuenteCalificacionIaCriteriosTest {

    private static final Long POSTULACION = 1L;
    private static final Long EVALUACION = 30L;

    @Mock private PostulacionRepository postulaciones;
    @Mock private RespuestaRepository respuestas;
    @Mock private PreguntaRepository preguntas;
    @Mock private PreguntaDimensionRepository preguntaDimensiones;
    @Mock private NotaRespuestaRepository notasRespuesta;
    @Mock private CalificacionCriterios calificacionCriterios;

    @InjectMocks
    private PuenteCalificacionIaImpl puente;

    private final Postulacion postulacion = Postulacion.builder()
            .id(POSTULACION).evaluacionId(EVALUACION).build();

    // Dos abiertas: R11 con la regla dura marcada, R18 normal.
    private final Pregunta r11 = Pregunta.builder().id(111L).codigo("R11").tipo("ABIERTA")
            .esPuntuable(true).logicaInterna("[TOPE_SIN_DATO=2] REGLA DURA…").build();
    private final Pregunta r18 = Pregunta.builder().id(118L).codigo("R18").tipo("ABIERTA")
            .esPuntuable(true).build();

    @BeforeEach
    void armar() {
        lenient().when(postulaciones.findById(POSTULACION)).thenReturn(Optional.of(postulacion));
        lenient().when(respuestas.findByEvaluacionId(EVALUACION)).thenReturn(List.of(
                Respuesta.builder().id(211L).preguntaId(111L).evaluacionId(EVALUACION)
                        .texto("Tenía 14 personas…").build(),
                Respuesta.builder().id(218L).preguntaId(118L).evaluacionId(EVALUACION)
                        .texto("Lo dejaría pasar.").build()));
        lenient().when(preguntas.findByIdIn(anyList())).thenReturn(List.of(r11, r18));
        lenient().when(preguntaDimensiones.findByPreguntaIdIn(anyList())).thenReturn(List.of());
        lenient().when(calificacionCriterios.metodoDe(postulacion)).thenReturn("CRITERIOS");
    }

    private static NotaRespuestaIa nota(Long respuestaId, boolean senal, boolean c1, boolean c2,
                                        boolean c3, boolean c4) {
        return new NotaRespuestaIa(respuestaId, null, "porque sí", "cita literal",
                new BigDecimal("80"), senal, c1, c2, c3, c4);
    }

    @Test
    @DisplayName("El puntaje sale de contar los criterios, con la regla dura y la señal de 0")
    void cuentaLosCriterios() {
        when(notasRespuesta.findByRespuestaId(any())).thenReturn(Optional.empty());

        puente.guardarNotasAbiertas(POSTULACION, 77L, new ResultadoEvaluador(List.of(
                // R11: episodio suyo con incomodidad pero SIN cifra → contaría 3, el tope lo deja en 2.
                nota(211L, false, true, true, false, true),
                // R18: cumple la señal de 0 → 0, aunque los criterios estén.
                nota(218L, true, true, true, true, true))));

        ArgumentCaptor<NotaRespuesta> guardadas = ArgumentCaptor.forClass(NotaRespuesta.class);
        verify(notasRespuesta, org.mockito.Mockito.times(2)).save(guardadas.capture());

        NotaRespuesta deR11 = guardadas.getAllValues().stream()
                .filter(n -> n.getRespuestaId().equals(211L)).findFirst().orElseThrow();
        assertThat(deR11.getPuntaje()).isEqualByComparingTo(new BigDecimal("2"));
        assertThat(deR11.getC1Episodio()).isTrue();
        assertThat(deR11.getC3Dato()).isFalse();
        assertThat(deR11.getCumpleSenalCero()).isFalse();
        assertThat(deR11.getEjecucionIaId()).isEqualTo(77L);

        NotaRespuesta deR18 = guardadas.getAllValues().stream()
                .filter(n -> n.getRespuestaId().equals(218L)).findFirst().orElseThrow();
        assertThat(deR18.getPuntaje()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(deR18.getCumpleSenalCero()).isTrue();

        // Con la tanda completa, la nota de etapa se recalcula.
        verify(calificacionCriterios).calificarEtapa(postulacion);
    }

    @Test
    @DisplayName("Media tanda no es una nota: si el agente omite una respuesta, se revienta y se reintenta")
    void mediaTandaRevienta() {
        when(notasRespuesta.findByRespuestaId(211L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> puente.guardarNotasAbiertas(POSTULACION, 77L,
                new ResultadoEvaluador(List.of(nota(211L, false, true, true, true, false)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sin calificar");
        verify(calificacionCriterios, never()).calificarEtapa(any());
    }

    @Test
    @DisplayName("Una nota sin los criterios declarados se descarta: aquí no valen números sueltos")
    void sinCriteriosNoVale() {
        // El agente contesta con puntaje (el contrato viejo) en un banco CRITERIOS.
        NotaRespuestaIa soloNumero = new NotaRespuestaIa(211L, new BigDecimal("3"),
                "porque sí", "cita", null, null, null, null, null, null);

        assertThatThrownBy(() -> puente.guardarNotasAbiertas(POSTULACION, 77L,
                new ResultadoEvaluador(List.of(soloNumero,
                        nota(218L, false, true, false, false, false)))))
                .isInstanceOf(IllegalStateException.class);
        // La descartada nunca se guardó.
        verify(notasRespuesta, never()).save(org.mockito.ArgumentMatchers.argThat(
                n -> n.getRespuestaId().equals(211L)));
    }

    @Test
    @DisplayName("Lo ajustado a mano no se pisa, pero cuenta como calificado")
    void loAjustadoCuentaSinPisarse() {
        when(notasRespuesta.findByRespuestaId(211L)).thenReturn(Optional.of(NotaRespuesta.builder()
                .respuestaId(211L).puntaje(new BigDecimal("4"))
                .ajustadaPorUsuarioId(9L).build()));
        when(notasRespuesta.findByRespuestaId(218L)).thenReturn(Optional.empty());

        puente.guardarNotasAbiertas(POSTULACION, 77L, new ResultadoEvaluador(List.of(
                nota(211L, false, true, false, false, false),
                nota(218L, false, true, true, true, true))));

        // Solo se guardó la 218: la ajustada quedó intacta y aun así la tanda está completa.
        ArgumentCaptor<NotaRespuesta> guardadas = ArgumentCaptor.forClass(NotaRespuesta.class);
        verify(notasRespuesta).save(guardadas.capture());
        assertThat(guardadas.getValue().getRespuestaId()).isEqualTo(218L);
        verify(calificacionCriterios).calificarEtapa(postulacion);
    }
}
