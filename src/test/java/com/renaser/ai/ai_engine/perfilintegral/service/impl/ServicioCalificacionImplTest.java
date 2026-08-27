package com.renaser.ai.ai_engine.perfilintegral.service.impl;

import com.renaser.ai.ai_engine.pesos.entity.VersionPesos;
import com.renaser.ai.ai_engine.pesos.repository.VersionPesosRepository;
import com.renaser.ai.ai_engine.perfilintegral.entity.Evaluacion;
import com.renaser.ai.ai_engine.perfilintegral.entity.Opcion;
import com.renaser.ai.ai_engine.perfilintegral.entity.ParConsistencia;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Respuesta;
import com.renaser.ai.ai_engine.perfilintegral.repository.AlertaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.OpcionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.ParConsistenciaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.RespuestaRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El caso que la V20 dejó latente: los pares de consistencia del v3 no traen
 * diferencia_maxima (su regla es la penalización del −5%, del motor pendiente). La
 * comparación v0.1 tiene que saltárselos, no reventar con un NPE al entregar — que es
 * exactamente lo que pasaría si las dos preguntas del par se respondieran con opción.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("La calificación con pares de consistencia del v3")
class ServicioCalificacionImplTest {

    @Mock private PostulacionRepository postulaciones;
    @Mock private EvaluacionRepository evaluaciones;
    @Mock private RespuestaRepository respuestas;
    @Mock private PreguntaRepository preguntas;
    @Mock private OpcionRepository opciones;
    @Mock private ParConsistenciaRepository pares;
    @Mock private AlertaRepository alertas;
    @Mock private NotaEtapaRepository notasEtapa;
    @Mock private VersionPesosRepository versionesPesos;
    @Mock private VacanteRepository vacantes;
    // Vacío por defecto: banco sin método declarado, que es el camino v3 de estos tests.
    @Mock private com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository versionesBanco;

    @InjectMocks
    private ServicioCalificacionImpl servicio;

    @Test
    @DisplayName("un par sin diferencia máxima no revienta ni levanta alerta: su regla es del motor pendiente")
    void unParDelV3NoRevientaNiAlerta() {
        when(postulaciones.findById(50L)).thenReturn(Optional.of(
                Postulacion.builder().id(50L).evaluacionId(60L).vacanteId(5L).build()));
        when(evaluaciones.findById(60L)).thenReturn(Optional.of(
                Evaluacion.builder().id(60L).versionBancoNivelId(30L).build()));

        // Las dos preguntas del par, respondidas con opción: el peor caso, porque es el
        // único camino en que la comparación llega hasta la diferencia.
        when(respuestas.findByEvaluacionId(60L)).thenReturn(List.of(
                Respuesta.builder().evaluacionId(60L).preguntaId(1L).opcionId(11L).build(),
                Respuesta.builder().evaluacionId(60L).preguntaId(2L).opcionId(12L).build()));
        when(preguntas.findByIdIn(anyList())).thenReturn(List.of(
                Pregunta.builder().id(1L).tipo("PC").esPuntuable(false).build(),
                Pregunta.builder().id(2L).tipo("PC").esPuntuable(false).build()));
        // Las opciones de la tanda llegan en una sola consulta, no una por respuesta: el
        // banco v3 se aplica entero y pedirlas de una en una eran 190 viajes por entrega.
        when(opciones.findByPreguntaIdIn(anyList())).thenReturn(List.of(
                Opcion.builder().id(11L).preguntaId(1L).letra("A")
                        .puntaje(BigDecimal.valueOf(4)).build(),
                Opcion.builder().id(12L).preguntaId(2L).letra("A")
                        .puntaje(BigDecimal.ZERO).build()));

        // El par v3: sin diferencia_maxima, con la regla nueva que el motor aún no aplica.
        when(pares.findByVersionBancoId(30L)).thenReturn(List.of(ParConsistencia.builder()
                .versionBancoId(30L).preguntaAId(1L).preguntaBId(2L)
                .diferenciaMaxima(null)
                .penalizacionPorcentaje(BigDecimal.valueOf(5))
                .separacionMinimaItems((short) 15)
                .build()));

        when(vacantes.findById(5L)).thenReturn(Optional.of(
                Vacante.builder().id(5L).versionPesosId(7L).build()));
        when(versionesPesos.findById(7L)).thenReturn(Optional.of(
                VersionPesos.builder().id(7L).build()));
        when(notasEtapa.findByPostulacionIdAndEtapaCodigo(50L, "PERFIL_INTEGRAL"))
                .thenReturn(Optional.empty());

        assertThatCode(() -> servicio.calificarLoCerrado(50L)).doesNotThrowAnyException();
        verify(alertas, never()).save(any());
    }

    @Test
    @DisplayName("Las opciones del banco se leen una sola vez, no una por respuesta")
    void lasOpcionesSeLeenEnBloque() {
        // El examen del v3 se aplica entero: 190 ítems. Puntuarlos pidiendo las opciones
        // dentro del bucle eran 190 consultas, y comparar después los pares de consistencia
        // otras tantas. Eso lo pagaba el candidato esperando delante de «entregar», la única
        // petición del recorrido que no se puede reintentar sin rehacer el examen.
        List<Pregunta> banco = new ArrayList<>();
        List<Respuesta> dadas = new ArrayList<>();
        List<Opcion> todas = new ArrayList<>();
        for (long i = 1; i <= 190; i++) {
            banco.add(Pregunta.builder().id(i).tipo("PC").peso((short) 1).esPuntuable(false).build());
            dadas.add(Respuesta.builder().evaluacionId(60L).preguntaId(i).opcionId(1000 + i).build());
            todas.add(Opcion.builder().id(1000 + i).preguntaId(i).letra("A")
                    .puntaje(BigDecimal.ONE).build());
        }

        when(postulaciones.findById(50L)).thenReturn(Optional.of(
                Postulacion.builder().id(50L).evaluacionId(60L).vacanteId(5L).build()));
        when(evaluaciones.findById(60L)).thenReturn(Optional.of(
                Evaluacion.builder().id(60L).versionBancoNivelId(30L).build()));
        when(respuestas.findByEvaluacionId(60L)).thenReturn(dadas);
        when(preguntas.findByIdIn(anyList())).thenReturn(banco);
        when(opciones.findByPreguntaIdIn(anyList())).thenReturn(todas);
        when(pares.findByVersionBancoId(30L)).thenReturn(List.of(ParConsistencia.builder()
                .versionBancoId(30L).preguntaAId(1L).preguntaBId(2L)
                .diferenciaMaxima(BigDecimal.valueOf(2)).build()));
        when(vacantes.findById(5L)).thenReturn(Optional.of(
                Vacante.builder().id(5L).versionPesosId(7L).build()));
        when(versionesPesos.findById(7L)).thenReturn(Optional.of(VersionPesos.builder().id(7L).build()));
        when(notasEtapa.findByPostulacionIdAndEtapaCodigo(50L, "PERFIL_INTEGRAL"))
                .thenReturn(Optional.empty());

        servicio.calificarLoCerrado(50L);

        verify(opciones, times(1)).findByPreguntaIdIn(anyList());
        // Las dos formas de pedirlas de una en una. Si vuelve cualquiera de ellas al bucle,
        // el conteo de arriba sigue en 1 y solo esto lo delata.
        verify(opciones, never()).findByPreguntaIdOrderByLetra(any());
        verify(opciones, never()).findById(any());
    }

    @Test
    @DisplayName("un banco CRITERIOS no tiene nada cerrado: aquí no se escribe ninguna nota")
    void unBancoCriteriosSeSalta() {
        // Su nota de etapa la escribe CalificacionCriterios cuando el evaluador termina.
        // Escribir aquí un 0 sería una nota falsa esperando a ser sobrescrita.
        when(postulaciones.findById(50L)).thenReturn(Optional.of(
                Postulacion.builder().id(50L).evaluacionId(60L).vacanteId(5L).build()));
        when(evaluaciones.findById(60L)).thenReturn(Optional.of(
                Evaluacion.builder().id(60L).versionBancoNivelId(30L).build()));
        when(versionesBanco.findById(30L)).thenReturn(Optional.of(
                com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco.builder()
                        .id(30L).metodoCalificacion("CRITERIOS").build()));

        org.assertj.core.api.Assertions.assertThat(servicio.calificarLoCerrado(50L))
                .isEqualByComparingTo(BigDecimal.ZERO);

        verify(notasEtapa, never()).save(any());
        verify(respuestas, never()).findByEvaluacionId(any());
    }
}
