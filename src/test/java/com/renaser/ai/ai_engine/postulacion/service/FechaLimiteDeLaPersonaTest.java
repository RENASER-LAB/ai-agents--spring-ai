package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.perfilintegral.entity.Evaluacion;
import com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;
import com.renaser.ai.ai_engine.prueba.repository.IntentoPruebaRepository;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** La fecha que rige para cada persona (V70, AC-21): la suya antes que la de la vacante. */
@ExtendWith(MockitoExtension.class)
@DisplayName("La fecha límite de la persona")
class FechaLimiteDeLaPersonaTest {

    @Mock private IntentoPruebaRepository intentos;
    @Mock private EvaluacionRepository evaluaciones;
    @InjectMocks private FechaLimiteDeLaPersona fechas;

    private final Instant deLaVacante = Instant.now().plus(Duration.ofDays(5));
    private final Postulacion postulacion = Postulacion.builder().id(3L).build();

    @Test
    @DisplayName("con plazo propio manda el suyo, no el de la vacante")
    void elPropioManda() {
        Instant suyo = deLaVacante.plus(Duration.ofDays(2));
        when(intentos.findByPostulacionId(3L)).thenReturn(Optional.of(
                IntentoPrueba.builder().venceEn(suyo).plazoPropio(true).build()));

        assertThat(fechas.deSuPrueba(postulacion, Vacante.builder().pruebaCierraEn(deLaVacante).build()))
                .isEqualTo(suyo);
    }

    @Test
    @DisplayName("sin intento todavía, la de la vacante; sin ninguna, nada")
    void sinIntentoLaDeLaVacante() {
        when(intentos.findByPostulacionId(3L)).thenReturn(Optional.empty());

        assertThat(fechas.deSuPrueba(postulacion, Vacante.builder().pruebaCierraEn(deLaVacante).build()))
                .isEqualTo(deLaVacante);
        assertThat(fechas.deSuPrueba(postulacion, Vacante.builder().build())).isNull();
        assertThat(fechas.deSuPrueba(postulacion, null)).isNull();
    }

    @Test
    @DisplayName("el cuestionario técnico lleva la suya en su evaluación")
    void elCuestionarioLaSuya() {
        Instant delCuestionario = deLaVacante.minus(Duration.ofDays(1));
        Postulacion conCuestionario = Postulacion.builder().id(3L).evaluacionTecnicaId(8L).build();
        when(evaluaciones.findById(8L)).thenReturn(Optional.of(
                Evaluacion.builder().id(8L).venceEn(delCuestionario).build()));
        Vacante vacante = Vacante.builder().instrumentoEtapaTecnica("CUESTIONARIO_TECNICO").build();

        assertThat(fechas.deSuPrueba(conCuestionario, vacante)).isEqualTo(delCuestionario);
        assertThat(fechas.deSuPrueba(postulacion, vacante)).as("sin cuestionario creado").isNull();
    }
}
