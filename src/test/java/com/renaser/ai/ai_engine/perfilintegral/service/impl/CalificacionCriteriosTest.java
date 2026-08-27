package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.perfilintegral.entity.Evaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaEtapa;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaRespuesta;
import com.renaser.ai.ai_engine.perfilintegral.entity.PesoDimension;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.PreguntaDimension;
import com.renaser.ai.ai_engine.perfilintegral.entity.Respuesta;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaRespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PesoDimensionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaDimensionRepository;
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
 * La nota de etapa de un banco CRITERIOS: agregación por pilar contra un caso calculado a
 * mano, y las dos reglas que evitan una nota falsa — media rúbrica no escribe nada, y una
 * versión de pesos de otro instrumento tampoco.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("La nota de etapa por criterios")
class CalificacionCriteriosTest {

    private static final Long EVALUACION = 30L;
    private static final Long BANCO = 40L;
    private static final Long VACANTE = 50L;
    private static final Long PESOS = 60L;

    @Mock private EvaluacionRepository evaluaciones;
    @Mock private VersionBancoRepository versionesBanco;
    @Mock private RespuestaRepository respuestas;
    @Mock private PreguntaRepository preguntas;
    @Mock private PreguntaDimensionRepository preguntaDimensiones;
    @Mock private NotaRespuestaRepository notasRespuesta;
    @Mock private PesoDimensionRepository pesosDimension;
    @Mock private NotaEtapaRepository notasEtapa;
    @Mock private VacanteRepository vacantes;

    @InjectMocks
    private CalificacionCriterios servicio;

    private final Postulacion postulacion = Postulacion.builder()
            .id(1L).evaluacionId(EVALUACION).vacanteId(VACANTE).build();

    @BeforeEach
    void banco() {
        lenient().when(evaluaciones.findById(EVALUACION)).thenReturn(Optional.of(
                Evaluacion.builder().id(EVALUACION).versionBancoNivelId(BANCO).build()));
        lenient().when(versionesBanco.findById(BANCO)).thenReturn(Optional.of(
                VersionBanco.builder().id(BANCO).nivelPuestoCodigo("DIRECCION")
                        .metodoCalificacion("CRITERIOS").build()));
    }

    // Un banco de juguete con la forma del real: dos pilares ponderados y la integridad,
    // que se puntúa pero no pondera. R11 con peso 2, como en el instrumento.
    private void armarBanco(boolean conTodasLasNotas) {
        List<Pregunta> lasPreguntas = List.of(
                pregunta(101L, "R01", 1), pregunta(111L, "R11", 2), pregunta(118L, "R18", 1));
        when(preguntas.findByVersionBancoIdOrderByOrden(BANCO)).thenReturn(lasPreguntas);
        when(respuestas.findByEvaluacionId(EVALUACION)).thenReturn(List.of(
                respuesta(201L, 101L), respuesta(211L, 111L), respuesta(218L, 118L)));
        when(preguntaDimensiones.findByPreguntaIdIn(anyList())).thenReturn(List.of(
                dimension(101L, "PIL_INICIATIVA"),
                dimension(111L, "PIL_RESPONSABILIDAD"),
                dimension(118L, "PIL_INTEGRIDAD")));

        // R01 un 2 · R11 un 4 (peso 2 → aporta 8 de 8) · R18 un 3.
        List<NotaRespuesta> notas = conTodasLasNotas
                ? List.of(nota(201L, 2), nota(211L, 4), nota(218L, 3))
                : List.of(nota(201L, 2), nota(218L, 3));
        when(notasRespuesta.findByRespuestaIdIn(anyList())).thenReturn(notas);
    }

    @Test
    @DisplayName("Con todas las notas, el índice sale de los pilares ponderados")
    void calculaElIndice() {
        armarBanco(true);
        when(vacantes.findById(VACANTE)).thenReturn(Optional.of(
                Vacante.builder().id(VACANTE).versionPesosId(PESOS).build()));
        // Iniciativa pesa 40 y Responsabilidad 60. Integridad no pondera: es eliminatoria.
        when(pesosDimension.findByVersionPesosId(PESOS)).thenReturn(List.of(
                peso("PIL_INICIATIVA", 40), peso("PIL_RESPONSABILIDAD", 60)));
        when(notasEtapa.findByPostulacionIdAndEtapaCodigo(1L, "PERFIL_INTEGRAL"))
                .thenReturn(Optional.empty());

        servicio.calificarEtapa(postulacion);

        ArgumentCaptor<NotaEtapa> guardada = ArgumentCaptor.forClass(NotaEtapa.class);
        verify(notasEtapa).save(guardada.capture());
        // Iniciativa: 2 de 4 = 50 · Responsabilidad: 8 de 8 = 100.
        // Índice = (50 × 40 + 100 × 60) ÷ 100 = 80.
        assertThat(guardada.getValue().getPuntaje())
                .isEqualByComparingTo(new BigDecimal("80.00"));
        assertThat(guardada.getValue().getVersionPesosId()).isEqualTo(PESOS);
        assertThat(guardada.getValue().getEtapaCodigo()).isEqualTo("PERFIL_INTEGRAL");
    }

    @Test
    @DisplayName("Media rúbrica no es una nota: si falta una calificación no se escribe nada")
    void sinTodasLasNotasNoEscribe() {
        armarBanco(false);

        servicio.calificarEtapa(postulacion);

        verify(notasEtapa, never()).save(any());
    }

    @Test
    @DisplayName("Una versión de pesos sin pilares deja la postulación sin nota, no con una inventada")
    void pesosDeOtroInstrumentoNoEscriben() {
        armarBanco(true);
        when(vacantes.findById(VACANTE)).thenReturn(Optional.of(
                Vacante.builder().id(VACANTE).versionPesosId(PESOS).build()));
        // La vacante apunta a los pesos del v3: dimensiones, no pilares.
        when(pesosDimension.findByVersionPesosId(PESOS)).thenReturn(List.of(peso("INT", 20)));

        servicio.calificarEtapa(postulacion);

        verify(notasEtapa, never()).save(any());
    }

    @Test
    @DisplayName("Un banco que no es CRITERIOS ni se mira")
    void otroBancoNoHaceNada() {
        when(versionesBanco.findById(BANCO)).thenReturn(Optional.of(
                VersionBanco.builder().id(BANCO).metodoCalificacion(null).build()));

        servicio.calificarEtapa(postulacion);

        verify(notasEtapa, never()).save(any());
    }

    // ---------- fábricas ----------

    private static Pregunta pregunta(Long id, String codigo, int peso) {
        return Pregunta.builder().id(id).codigo(codigo).esPuntuable(true)
                .peso((short) peso).tipo("ABIERTA").build();
    }

    private static Respuesta respuesta(Long id, Long preguntaId) {
        return Respuesta.builder().id(id).preguntaId(preguntaId).evaluacionId(EVALUACION).build();
    }

    private static PreguntaDimension dimension(Long preguntaId, String codigo) {
        return PreguntaDimension.builder().preguntaId(preguntaId).dimensionCodigo(codigo).build();
    }

    private static NotaRespuesta nota(Long respuestaId, int puntaje) {
        return NotaRespuesta.builder().respuestaId(respuestaId)
                .puntaje(BigDecimal.valueOf(puntaje)).build();
    }

    private static PesoDimension peso(String dimension, int peso) {
        return PesoDimension.builder().versionPesosId(PESOS).nivelPuestoCodigo("DIRECCION")
                .dimensionCodigo(dimension).peso(BigDecimal.valueOf(peso)).build();
    }
}
