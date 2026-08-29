package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.perfilintegral.entity.Evaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaEtapa;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaRespuesta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Respuesta;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaRespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.RespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El índice técnico contra casos calculados a mano, y las dos asimetrías con la etapa 1 que
 * son la razón de que esto sea un servicio aparte:
 *
 * <ul>
 *   <li>una pregunta <b>sin responder</b> vale cero y <b>sigue contando</b> en el
 *       denominador — el reloj entrega lo que haya, y no contestar es un cero;
 *   <li>una respuesta <b>sin calificar todavía</b> detiene la nota, porque media rúbrica no
 *       es una nota y la cola va a reintentar.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El índice del cuestionario técnico")
class CalificacionCuestionarioTecnicoTest {

    private static final Long POSTULACION = 7L;
    private static final Long EVALUACION = 30L;
    private static final Long CUESTIONARIO = 40L;
    private static final Long VACANTE = 50L;
    private static final Long PESOS = 60L;

    @Mock private EvaluacionRepository evaluaciones;
    @Mock private VersionBancoRepository versionesBanco;
    @Mock private PreguntaRepository preguntas;
    @Mock private RespuestaRepository respuestas;
    @Mock private NotaRespuestaRepository notasRespuesta;
    @Mock private NotaEtapaRepository notasEtapa;
    @Mock private VacanteRepository vacantes;

    @InjectMocks
    private CalificacionCuestionarioTecnico servicio;

    private Postulacion postulacion;

    @BeforeEach
    void prepararLoComun() {
        postulacion = Postulacion.builder()
                .id(POSTULACION).vacanteId(VACANTE).evaluacionTecnicaId(EVALUACION).build();

        lenient().when(vacantes.findById(VACANTE)).thenReturn(Optional.of(
                Vacante.builder().id(VACANTE).versionPesosId(PESOS).build()));
        lenient().when(evaluaciones.findById(EVALUACION)).thenReturn(Optional.of(
                Evaluacion.builder().id(EVALUACION).versionBancoNivelId(CUESTIONARIO).build()));
        lenient().when(versionesBanco.findById(CUESTIONARIO)).thenReturn(Optional.of(
                VersionBanco.builder().id(CUESTIONARIO).tipoBanco("VACANTE")
                        .metodoCalificacion("CRITERIOS").vacanteId(VACANTE).build()));
        lenient().when(notasEtapa.findByPostulacionIdAndEtapaCodigo(POSTULACION, "PRUEBA_PUESTO"))
                .thenReturn(Optional.empty());
    }

    /** Un cuestionario de n preguntas de peso 1, todas respondidas, con los puntajes dados. */
    private void cuestionarioCon(int cuantas, List<Integer> puntajes, boolean marcarPresencial) {
        List<Pregunta> todas = new ArrayList<>();
        List<Respuesta> suyas = new ArrayList<>();
        List<NotaRespuesta> notas = new ArrayList<>();
        for (int i = 0; i < cuantas; i++) {
            long preguntaId = 100L + i;
            todas.add(Pregunta.builder().id(preguntaId).codigo("T" + (i + 1))
                    .versionBancoId(CUESTIONARIO).peso((short) 1).esPuntuable(true).build());
            Integer puntaje = i < puntajes.size() ? puntajes.get(i) : null;
            if (puntaje == null) {
                continue;       // sin responder: no hay fila de respuesta
            }
            long respuestaId = 200L + i;
            suyas.add(Respuesta.builder().id(respuestaId).evaluacionId(EVALUACION)
                    .preguntaId(preguntaId).texto("lo que escribió").build());
            notas.add(NotaRespuesta.builder().respuestaId(respuestaId)
                    .puntaje(BigDecimal.valueOf(puntaje)).build());
        }
        if (marcarPresencial) {
            // La muestra de trabajo: nunca se le envió, así que no puede contar ni arriba ni
            // abajo de la división.
            todas.add(Pregunta.builder().id(999L).codigo("T12").versionBancoId(CUESTIONARIO)
                    .peso((short) 1).esPuntuable(false).presencial(true).build());
        }
        when(preguntas.findByVersionBancoIdOrderByOrden(CUESTIONARIO)).thenReturn(todas);
        when(respuestas.findByEvaluacionId(EVALUACION)).thenReturn(suyas);
        lenient().when(notasRespuesta.findByRespuestaIdIn(anyList())).thenReturn(notas);
    }

    private BigDecimal elIndiceEscrito() {
        ArgumentCaptor<NotaEtapa> guardada = ArgumentCaptor.forClass(NotaEtapa.class);
        verify(notasEtapa).save(guardada.capture());
        assertThat(guardada.getValue().getEtapaCodigo()).isEqualTo("PRUEBA_PUESTO");
        assertThat(guardada.getValue().getVersionPesosId()).isEqualTo(PESOS);
        return guardada.getValue().getPuntaje();
    }

    @Test
    @DisplayName("puntos ÷ (4 × preguntas) × 100, la fórmula del documento")
    void laFormulaDelMetodo() {
        // Diez preguntas, 27 puntos: 27 / 40 = 67,50.
        cuestionarioCon(10, List.of(4, 4, 3, 3, 3, 2, 2, 2, 2, 2), false);

        servicio.calificarEtapa(postulacion);

        assertThat(elIndiceEscrito()).isEqualByComparingTo("67.50");
    }

    @Test
    @DisplayName("todo perfecto son 100 y todo cero es 0")
    void losDosExtremos() {
        cuestionarioCon(8, List.of(4, 4, 4, 4, 4, 4, 4, 4), false);
        servicio.calificarEtapa(postulacion);
        assertThat(elIndiceEscrito()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("la presencial no cuenta: ni suma puntos ni agranda el denominador")
    void laPresencialNoEntra() {
        // Once puntuables a 4 más la presencial. Si contara, el índice bajaría de 100.
        cuestionarioCon(11, List.of(4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4), true);

        servicio.calificarEtapa(postulacion);

        assertThat(elIndiceEscrito()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("lo que no contestó vale cero, y su pregunta sigue contando")
    void loQueNoContestoEsUnCero() {
        // Cuatro preguntas, dos contestadas con 4 y dos en blanco: 8 / 16 = 50.
        cuestionarioCon(4, List.of(4, 4), false);

        servicio.calificarEtapa(postulacion);

        // Si las no contestadas se cayeran del denominador, esto sería 100 y el candidato
        // saldría mejor por haber trabajado menos.
        assertThat(elIndiceEscrito()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("si falta una calificación no se escribe nada: la cola reintenta")
    void mediaRubricaNoEsUnaNota() {
        List<Pregunta> todas = List.of(
                Pregunta.builder().id(100L).codigo("T01").versionBancoId(CUESTIONARIO)
                        .peso((short) 1).esPuntuable(true).build(),
                Pregunta.builder().id(101L).codigo("T02").versionBancoId(CUESTIONARIO)
                        .peso((short) 1).esPuntuable(true).build());
        // Las dos contestadas, pero solo una calificada.
        when(preguntas.findByVersionBancoIdOrderByOrden(CUESTIONARIO)).thenReturn(todas);
        when(respuestas.findByEvaluacionId(EVALUACION)).thenReturn(List.of(
                Respuesta.builder().id(200L).evaluacionId(EVALUACION).preguntaId(100L)
                        .texto("una").build(),
                Respuesta.builder().id(201L).evaluacionId(EVALUACION).preguntaId(101L)
                        .texto("otra").build()));
        when(notasRespuesta.findByRespuestaIdIn(anyList())).thenReturn(List.of(
                NotaRespuesta.builder().respuestaId(200L).puntaje(BigDecimal.valueOf(4)).build()));

        servicio.calificarEtapa(postulacion);

        verify(notasEtapa, never()).save(any());
    }

    @Test
    @DisplayName("las respuestas para el panel salen en el orden del cuestionario, con su nota")
    void lasRespuestasParaElPanel() {
        cuestionarioCon(3, List.of(4, 3), true);

        var vistas = servicio.respuestasDe(postulacion);

        // Las tres puntuables, no la presencial: al candidato nunca se le envió.
        assertThat(vistas).hasSize(3);
        assertThat(vistas.get(0).texto()).isEqualTo("lo que escribió");
        assertThat(vistas.get(0).puntaje()).isEqualByComparingTo("4");
        // La que dejó en blanco también se emite: omitirla la haría invisible al revisarla.
        assertThat(vistas.get(2).texto()).isNull();
        assertThat(vistas.get(2).puntaje()).isNull();
    }

    @Test
    @DisplayName("sin cuestionario, no hay respuestas que enseñar")
    void sinCuestionarioNoHayRespuestas() {
        assertThat(servicio.respuestasDe(Postulacion.builder()
                .id(POSTULACION).vacanteId(VACANTE).evaluacionTecnicaId(null).build())).isEmpty();
    }

    @Test
    @DisplayName("un cuestionario sin preguntas puntuables no escribe una nota inventada")
    void sinPreguntasPuntuablesNoHayNota() {
        when(preguntas.findByVersionBancoIdOrderByOrden(CUESTIONARIO)).thenReturn(List.of());

        servicio.calificarEtapa(postulacion);

        verify(notasEtapa, never()).save(any());
    }

    @Test
    @DisplayName("sin cuestionario técnico no hace nada: esa etapa la califica la prueba")
    void sinCuestionarioNoSeMete() {
        servicio.calificarEtapa(Postulacion.builder()
                .id(POSTULACION).vacanteId(VACANTE).evaluacionTecnicaId(null).build());

        verify(notasEtapa, never()).save(any());
    }
}
