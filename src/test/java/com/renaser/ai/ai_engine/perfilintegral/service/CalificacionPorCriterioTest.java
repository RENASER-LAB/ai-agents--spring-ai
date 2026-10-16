package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaCriterio;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaEtapa;
import com.renaser.ai.ai_engine.perfilintegral.entity.PesoCriterio;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaCriterioRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PesoCriterioRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.PuestoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Poner y sumar las notas de la simulación y la validación con los pesos de la vacante, cuando
 * esos pesos pueden cambiar con candidatos dentro (V74, AC-03 y AC-05; QA-01 y QA-02).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Calificar por criterio con pesos que cambian")
class CalificacionPorCriterioTest {

    private static final Long POSTULACION = 11L;
    private static final Long VACANTE = 40L;
    private static final Long PUESTO = 4L;
    private static final Long CALIDAD = 502L;
    private static final ContextoUsuario FACILITADOR = new ContextoUsuario(
            7L, null, 1L, "EQUIPO", List.of(), Map.of());

    @Mock private CriterioRepository criterios;
    @Mock private NotaCriterioRepository notasCriterio;
    @Mock private NotaEtapaRepository notasEtapa;
    @Mock private PesoCriterioRepository pesosCriterio;
    @Mock private PostulacionRepository postulaciones;
    @Mock private VacanteRepository vacantes;
    @Mock private PuestoRepository puestos;

    private CalificacionPorCriterio calificacion;
    private final Postulacion postulacion = Postulacion.builder().id(POSTULACION)
            .vacanteId(VACANTE).build();
    private final Criterio calidad = Criterio.builder().id(CALIDAD).nombre("Calidad").build();

    @BeforeEach
    void armar() {
        calificacion = new CalificacionPorCriterio(criterios, notasCriterio, notasEtapa,
                pesosCriterio, postulaciones, vacantes, puestos);
    }

    @Test
    @DisplayName("QA-01 · poner una nota apunta cómo se puso y sobre cuánto, para que el "
            + "recálculo por pesos parta de ahí")
    void ponerNotaApuntaLaOriginal() {
        // Leída de antes con la versión 7 (calidad 15); el cambio a la 8 (calidad 10) ya
        // confirmó: el cerrojo la relee
        Vacante vacante = Vacante.builder().id(VACANTE).versionPesosId(7L).puestoId(PUESTO).build();
        when(postulaciones.findById(POSTULACION)).thenReturn(Optional.of(postulacion));
        when(vacantes.findById(VACANTE)).thenReturn(Optional.of(vacante));
        doAnswer(i -> {
            ((Vacante) i.getArgument(0)).setVersionPesosId(8L);
            return null;
        }).when(vacantes).bloquearParaCalificar(vacante);
        when(puestos.findById(PUESTO)).thenReturn(Optional.of(Puesto.builder().id(PUESTO)
                .nivelPuestoCodigo("EJECUCION").build()));
        when(pesosCriterio.findByVersionPesosIdAndNivelPuestoCodigoAndCriterioId(8L, "EJECUCION",
                CALIDAD)).thenReturn(Optional.of(PesoCriterio.builder().criterioId(CALIDAD)
                .peso(BigDecimal.TEN).build()));
        when(notasCriterio.findByPostulacionIdAndCriterioId(POSTULACION, CALIDAD))
                .thenReturn(Optional.empty());

        calificacion.ponerNota(FACILITADOR, POSTULACION, List.of(calidad), CALIDAD, 4.5,
                "Entregó con dos errores");

        ArgumentCaptor<NotaCriterio> guardada = ArgumentCaptor.forClass(NotaCriterio.class);
        verify(notasCriterio).save(guardada.capture());
        assertThat(guardada.getValue().getPuntaje()).isEqualByComparingTo("4.5");
        assertThat(guardada.getValue().getPuntajeOriginal()).isEqualByComparingTo("4.5");
        // Sobre el máximo de la versión que rige al escribirla, no el de la copia vieja
        assertThat(guardada.getValue().getMaximoOriginal()).isEqualByComparingTo("10");
        InOrder orden = inOrder(vacantes, pesosCriterio, notasCriterio);
        orden.verify(vacantes).bloquearParaCalificar(vacante);
        orden.verify(pesosCriterio, atLeastOnce()).findByVersionPesosIdAndNivelPuestoCodigoAndCriterioId(8L,
                "EJECUCION", CALIDAD);
        orden.verify(notasCriterio).save(any());
    }

    @Test
    @DisplayName("QA-02 · la nota de la etapa espera al cambio de pesos a mitad antes de leer las "
            + "notas, y sale con la versión nueva")
    void calcularNotaEtapaEsperaAlCambio() {
        Vacante vacante = Vacante.builder().id(VACANTE).versionPesosId(7L).build();
        when(vacantes.findById(VACANTE)).thenReturn(Optional.of(vacante));
        doAnswer(i -> {
            ((Vacante) i.getArgument(0)).setVersionPesosId(8L);
            return null;
        }).when(vacantes).bloquearParaCalificar(vacante);
        when(notasCriterio.findByPostulacionId(POSTULACION)).thenReturn(List.of(
                NotaCriterio.builder().postulacionId(POSTULACION).criterioId(CALIDAD)
                        .puntaje(new BigDecimal("4.67")).build()));
        when(notasEtapa.findByPostulacionIdAndEtapaCodigo(POSTULACION, "SIMULACION"))
                .thenReturn(Optional.empty());

        BigDecimal nota = calificacion.calcularNotaEtapa(postulacion, "SIMULACION", List.of(calidad));

        assertThat(nota).isEqualByComparingTo("4.67");
        ArgumentCaptor<NotaEtapa> guardada = ArgumentCaptor.forClass(NotaEtapa.class);
        verify(notasEtapa).save(guardada.capture());
        assertThat(guardada.getValue().getVersionPesosId()).isEqualTo(8L);
        InOrder orden = inOrder(vacantes, notasCriterio);
        orden.verify(vacantes).bloquearParaCalificar(vacante);
        orden.verify(notasCriterio).findByPostulacionId(POSTULACION);
    }
}
