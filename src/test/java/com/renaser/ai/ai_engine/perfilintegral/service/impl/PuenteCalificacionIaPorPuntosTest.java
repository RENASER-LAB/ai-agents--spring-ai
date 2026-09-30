package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.NotaRespuestaIa;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoEvaluador;
import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.CriterioBanco;
import com.renaser.ai.ai_engine.perfilintegral.entity.Evaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaCriterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaEtapa;
import com.renaser.ai.ai_engine.perfilintegral.entity.PesoCriterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaRespuesta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Respuesta;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.HallazgoPerfilRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaRespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PerfilTalentoRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PesoCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.CalificacionPorPuntos;
import com.renaser.ai.ai_engine.perfilintegral.service.DatosDeLaVacanteParaIa;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioCalificacion;
import com.renaser.ai.ai_engine.pesos.entity.PesoComponentePerfil;
import com.renaser.ai.ai_engine.pesos.repository.PesoComponentePerfilRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * La red de seguridad del backend para lo que devuelve la IA en las preguntas propias:
 * descarta lo ajeno y lo que no trae explicación, acota a cada máximo, descarta lo calculado
 * con una guía vieja, y al recalificar aplica las notas de una persona juntas o ninguna.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El puente guarda las notas por puntos con su red de seguridad")
class PuenteCalificacionIaPorPuntosTest {

    private static final long POSTULACION = 10L;
    private static final long EVALUACION = 50L;
    private static final long VERSION = 70L;

    @Mock private PostulacionRepository postulaciones;
    @Mock private EvaluacionRepository evaluaciones;
    @Mock private CalificacionPorPuntos porPuntos;
    @Mock private NotaRespuestaRepository notasRespuesta;
    @Mock private VacanteRepository vacantes;
    @Mock private PuestoRepository puestos;
    @Mock private PerfilTalentoRepository perfiles;
    @Mock private HallazgoPerfilRepository hallazgos;
    @Mock private ServicioCalificacion calificacion;
    @Mock private PesoComponentePerfilRepository pesosComponente;
    @Mock private NotaEtapaRepository notasEtapa;
    @Mock private ServicioParametros parametros;
    @Mock private MaquinaEstados maquina;
    @Mock private CriterioRepository criterios;
    @Mock private PesoCriterioRepository pesosCriterio;
    @Mock private NotaCriterioRepository notasCriterio;
    @Mock private DatosDeLaVacanteParaIa datosDeLaVacante;
    @InjectMocks private PuenteCalificacionIaImpl puente;

    private final Postulacion postulacion = Postulacion.builder().id(POSTULACION).organizacionId(1L)
            .vacanteId(13L).evaluacionId(EVALUACION).estadoCodigo("PERFIL_CALIFICANDO").build();
    private final VersionBanco version = VersionBanco.builder().id(VERSION)
            .metodoCalificacion("PUNTOS").versionGuia(2).build();

    @BeforeEach
    void armar() {
        lenient().when(postulaciones.findById(POSTULACION)).thenReturn(Optional.of(postulacion));
        lenient().when(evaluaciones.findById(EVALUACION)).thenReturn(Optional.of(
                Evaluacion.builder().id(EVALUACION).versionBancoNivelId(VERSION).build()));
        lenient().when(porPuntos.versionPorPuntos(VERSION)).thenReturn(Optional.of(version));
    }

    /** Dos abiertas: A de 15 puntos sin nota, B de 20 con una nota de la IA de la guía 1. */
    private void conDosAbiertas(NotaRespuesta deB) {
        Pregunta a = Pregunta.builder().id(1L).tipo("ABIERTA").enunciado("¿A?").puntos(15).build();
        Pregunta b = Pregunta.builder().id(2L).tipo("ABIERTA").enunciado("¿B?").puntos(20).build();
        Respuesta ra = Respuesta.builder().id(201L).preguntaId(1L).texto("Respuesta A").build();
        Respuesta rb = Respuesta.builder().id(202L).preguntaId(2L).texto("Respuesta B").build();
        List<CalificacionPorPuntos.PreguntaCalculada> preguntas = new ArrayList<>(List.of(
                CalificacionPorPuntos.calcularPregunta(a, List.of(), ra, null),
                CalificacionPorPuntos.calcularPregunta(b, List.of(), rb, deB)));
        CalificacionPorPuntos.CriterioCalculado criterio = new CalificacionPorPuntos.CriterioCalculado(
                CriterioBanco.builder().id(5L).nombre("Contabilidad").build(), 35, BigDecimal.ZERO, 0,
                BigDecimal.ZERO, 35, true, preguntas);
        when(porPuntos.calcular(VERSION, EVALUACION)).thenReturn(new CalificacionPorPuntos.Resultado(
                version, List.of(criterio), List.of(), BigDecimal.ZERO, false));
    }

    private static NotaRespuestaIa nota(long respuestaId, String puntaje, String explicacion) {
        return new NotaRespuestaIa(respuestaId, new BigDecimal(puntaje), explicacion, "cita",
                new BigDecimal("90"), null, null, null, null, null);
    }

    @Test
    @DisplayName("25 en una de 15 queda en 15; sin explicación o de otra evaluación, se descarta (AC-10)")
    void acotaYDescarta() {
        conDosAbiertas(null);

        boolean guardado = puente.guardarNotasPorPuntos(POSTULACION, 77L, new ResultadoEvaluador(List.of(
                nota(201L, "25", "Tiene el monto"),
                nota(202L, "12", "  "),
                nota(999L, "10", "Es de otra evaluación"))), 2, false);

        assertThat(guardado).isTrue();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<NotaRespuesta>> filas = ArgumentCaptor.forClass(Iterable.class);
        verify(notasRespuesta).saveAll(filas.capture());
        List<NotaRespuesta> guardadas = new ArrayList<>();
        filas.getValue().forEach(guardadas::add);
        assertThat(guardadas).singleElement().satisfies(n -> {
            assertThat(n.getRespuestaId()).isEqualTo(201L);
            assertThat(n.getPuntaje()).isEqualByComparingTo("15");
            assertThat(n.getVersionGuia()).isEqualTo(2);
            assertThat(n.getEjecucionIaId()).isEqualTo(77L);
        });
    }

    @Test
    @DisplayName("Un resultado calculado con una guía anterior se descarta entero")
    void guiaViejaSeDescarta() {
        boolean guardado = puente.guardarNotasPorPuntos(POSTULACION, 77L, new ResultadoEvaluador(List.of(
                nota(201L, "10", "Tiene el monto"))), 1, false);

        assertThat(guardado).isFalse();
        verify(notasRespuesta, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Al recalificar, si falta una abierta que ya tenía nota, no se aplica ninguna")
    void recalificarEsTodoONada() {
        conDosAbiertas(NotaRespuesta.builder().respuestaId(202L).puntaje(new BigDecimal("10"))
                .explicacion("vieja").versionGuia(1).build());

        assertThatThrownBy(() -> puente.guardarNotasPorPuntos(POSTULACION, 77L,
                new ResultadoEvaluador(List.of(nota(201L, "9", "Nueva guía"))), 2, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("juntas o ninguna");
        verify(notasRespuesta, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Al recalificar completo, recalcula nota y grupo sin mover a nadie de etapa")
    void recalificarRecalculaSinMover() {
        conDosAbiertas(NotaRespuesta.builder().respuestaId(202L).puntaje(new BigDecimal("10"))
                .explicacion("vieja").versionGuia(1).build());
        when(vacantes.findById(13L)).thenReturn(Optional.of(Vacante.builder().id(13L)
                .versionPesosId(2L).puestoId(4L).build()));
        when(puestos.findById(4L)).thenReturn(Optional.of(Puesto.builder().id(4L)
                .nivelPuestoCodigo("EJECUCION").build()));
        when(calificacion.notaDelBancoPorPuntos(POSTULACION)).thenReturn(new ServicioCalificacion.NotaDelBanco(
                new BigDecimal("80"), true, null, 0, null, Map.of()));
        when(pesosComponente.findByVersionPesosId(2L)).thenReturn(List.of(PesoComponentePerfil.builder()
                .componente("EVALUACION").peso(new BigDecimal("100")).build()));
        when(notasEtapa.findByPostulacionIdAndEtapaCodigo(POSTULACION, "PERFIL_INTEGRAL"))
                .thenReturn(Optional.empty());
        when(perfiles.findByPostulacionId(POSTULACION)).thenReturn(Optional.empty());
        when(parametros.entero(any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(80, 65);

        puente.guardarNotasPorPuntos(POSTULACION, 77L, new ResultadoEvaluador(List.of(
                nota(201L, "9", "Nueva guía"), nota(202L, "14", "Nueva guía"))), 2, true);

        ArgumentCaptor<NotaEtapa> etapa = ArgumentCaptor.forClass(NotaEtapa.class);
        verify(notasEtapa).save(etapa.capture());
        assertThat(etapa.getValue().getPuntaje()).isEqualByComparingTo("80");
        assertThat(postulacion.getGrupoPrioridad()).isEqualTo("ALTA");
        assertThat(postulacion.getEstadoCodigo()).isEqualTo("PERFIL_CALIFICANDO");
        verifyNoInteractions(maquina);
    }

    @Test
    @DisplayName("La nota del banco se mezcla con la del currículum con los pesos de la vacante; "
            + "los criterios del banco no entran en la del currículum (AC-11, AC-24)")
    void seMezclaConElCurriculumConLosPesosDeLaVacante() {
        when(vacantes.findById(13L)).thenReturn(Optional.of(Vacante.builder().id(13L)
                .versionPesosId(2L).puestoId(4L).build()));
        when(puestos.findById(4L)).thenReturn(Optional.of(Puesto.builder().id(4L)
                .nivelPuestoCodigo("EJECUCION").build()));
        when(calificacion.notaDelBancoPorPuntos(POSTULACION)).thenReturn(new ServicioCalificacion.NotaDelBanco(
                new BigDecimal("80"), true, null, 0, null, Map.of()));
        when(pesosComponente.findByVersionPesosId(2L)).thenReturn(List.of(
                PesoComponentePerfil.builder().componente("CV").peso(new BigDecimal("30")).build(),
                PesoComponentePerfil.builder().componente("EVALUACION").peso(new BigDecimal("70")).build()));
        // Los dos criterios del currículum, con pesos 1 y 1: su nota es 60. La fila del
        // criterio 99 (de otra etapa) está en nota_criterio y NO cuenta: los criterios del
        // banco ni siquiera viven ahí (van en criterio_banco), así que no pueden colarse.
        when(criterios.findByEtapaCodigoAndVersionPlantillaPruebaIdIsNullOrderByOrden("PERFIL_INTEGRAL"))
                .thenReturn(List.of(Criterio.builder().id(1L).build(), Criterio.builder().id(2L).build()));
        when(pesosCriterio.findByVersionPesosIdAndNivelPuestoCodigo(2L, "EJECUCION")).thenReturn(List.of(
                PesoCriterio.builder().criterioId(1L).peso(BigDecimal.ONE).build(),
                PesoCriterio.builder().criterioId(2L).peso(BigDecimal.ONE).build(),
                PesoCriterio.builder().criterioId(99L).peso(BigDecimal.TEN).build()));
        when(notasCriterio.findByPostulacionId(POSTULACION)).thenReturn(List.of(
                NotaCriterio.builder().criterioId(1L).puntaje(new BigDecimal("50")).build(),
                NotaCriterio.builder().criterioId(2L).puntaje(new BigDecimal("70")).build(),
                NotaCriterio.builder().criterioId(99L).puntaje(new BigDecimal("0")).build()));
        when(notasEtapa.findByPostulacionIdAndEtapaCodigo(POSTULACION, "PERFIL_INTEGRAL"))
                .thenReturn(Optional.empty());
        when(perfiles.findByPostulacionId(POSTULACION)).thenReturn(Optional.empty());
        when(parametros.entero(any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(80, 65);

        puente.recalcularSinMover(POSTULACION);

        // 60 × 30 + 80 × 70 = 1800 + 5600 → 74
        ArgumentCaptor<NotaEtapa> etapa = ArgumentCaptor.forClass(NotaEtapa.class);
        verify(notasEtapa).save(etapa.capture());
        assertThat(etapa.getValue().getPuntaje()).isEqualByComparingTo("74");
        assertThat(etapa.getValue().getVersionPesosId()).isEqualTo(2L);
        verifyNoInteractions(maquina);
    }

    @Test
    @DisplayName("Ochenta preguntas con treinta abiertas: las treinta van a la IA y todas se guardan (AC-31)")
    void ochentaPreguntasTreintaAbiertas() {
        List<CalificacionPorPuntos.PreguntaCalculada> todas = new ArrayList<>();
        for (long i = 1; i <= 80; i++) {
            boolean abierta = i <= 30;
            Pregunta p = Pregunta.builder().id(i).tipo(abierta ? "ABIERTA" : "OPCION_UNICA")
                    .enunciado("¿" + i + "?").puntos(abierta ? 2 : 1).orden((int) i).build();
            Respuesta r = abierta
                    ? Respuesta.builder().id(1000 + i).preguntaId(i).texto("Respuesta " + i).build()
                    : Respuesta.builder().id(1000 + i).preguntaId(i).build();
            todas.add(CalificacionPorPuntos.calcularPregunta(p, List.of(), r, null));
        }
        CalificacionPorPuntos.CriterioCalculado criterio = new CalificacionPorPuntos.CriterioCalculado(
                CriterioBanco.builder().id(5L).nombre("General").build(), 110, BigDecimal.ZERO, 50,
                BigDecimal.ZERO, 60, true, todas);
        when(porPuntos.calcular(VERSION, EVALUACION)).thenReturn(new CalificacionPorPuntos.Resultado(
                version, List.of(criterio), List.of(), BigDecimal.ZERO, false));
        when(vacantes.findById(13L)).thenReturn(Optional.of(Vacante.builder().id(13L).build()));

        var insumo = puente.insumoPorPuntos(POSTULACION);
        assertThat(insumo.respuestas()).hasSize(30)
                .extracting(a -> a.respuestaId()).doesNotHaveDuplicates();

        // Las notas de las tres tandas llegan juntas: se guardan las treinta
        List<NotaRespuestaIa> notas = new ArrayList<>();
        for (long i = 1; i <= 30; i++) {
            notas.add(nota(1000 + i, "2", "Cumple"));
        }
        assertThat(puente.guardarNotasPorPuntos(POSTULACION, 77L, new ResultadoEvaluador(notas), 2, false))
                .isTrue();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<NotaRespuesta>> filas = ArgumentCaptor.forClass(Iterable.class);
        verify(notasRespuesta).saveAll(filas.capture());
        assertThat(filas.getValue()).hasSize(30);
    }

    @Test
    @DisplayName("Los bancos de siempre: fuera del 0–4 se rechaza, no se acota (AC-23)")
    void elCeroACuatroDeSiempre() {
        PuenteCalificacionIaImpl.exigirEscalaDeCuatro(1L, new BigDecimal("4"));
        PuenteCalificacionIaImpl.exigirEscalaDeCuatro(1L, BigDecimal.ZERO);
        assertThatThrownBy(() -> PuenteCalificacionIaImpl.exigirEscalaDeCuatro(1L, new BigDecimal("4.5")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("0–4");
        assertThatThrownBy(() -> PuenteCalificacionIaImpl.exigirEscalaDeCuatro(1L, new BigDecimal("-1")))
                .isInstanceOf(IllegalStateException.class);
    }
}
