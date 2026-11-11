package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.entity.Evaluacion;
import com.renaser.ai.ai_engine.perfilintegral.repository.EvaluacionRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lo que pasa con el Perfil Integral cuando su vacante se cierra (V78).
 *
 * <p>Tres personas en la misma vacante, como en AC-09: una sin empezar y otra a medias
 * esperando la evaluación, y una tercera que el equipo movió a otra etapa sin que la terminara
 * (AC-13). Las dos primeras se cierran; la tercera solo pierde su evaluación.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El Perfil Integral termina cuando la vacante se cierra")
class FinDelPerfilIntegralTest {

    private static final long VACANTE = 40L;
    private static final Instant CIERRE = Instant.parse("2026-11-10T18:00:00Z");

    @Mock private EvaluacionRepository evaluaciones;
    @Mock private PostulacionRepository postulaciones;
    @Mock private MaquinaEstados maquina;
    @Mock private PlatformTransactionManager transacciones;

    private FinDelPerfilIntegral fin;

    @BeforeEach
    void crear() {
        fin = new FinDelPerfilIntegral(evaluaciones, postulaciones, maquina, transacciones);
    }

    private Evaluacion abierta(long id, String estado) {
        return Evaluacion.builder().id(id).proposito("PERFIL_INTEGRAL").estado(estado).build();
    }

    private Postulacion suPostulacion(long evaluacionId, long id, String estado) {
        Postulacion p = Postulacion.builder().id(id).vacanteId(VACANTE).estadoCodigo(estado)
                .evaluacionId(evaluacionId).build();
        when(postulaciones.findByEvaluacionId(evaluacionId)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    @DisplayName("sin empezar y a medias quedan VENCIDA con el cierre como límite, y sus "
            + "postulaciones se cierran con PLAZO_VENCIDO; la movida a otra etapa no (AC-09, AC-13)")
    void lasQueEsperabanSeCierran() {
        Evaluacion sinEmpezar = abierta(1L, "PENDIENTE");
        Evaluacion aMedias = abierta(2L, "EN_CURSO");
        Evaluacion movida = abierta(3L, "PENDIENTE");
        when(evaluaciones.sinTerminarDelPerfilDeLaVacante(VACANTE))
                .thenReturn(List.of(sinEmpezar, aMedias, movida));
        Postulacion ana = suPostulacion(1L, 11L, "PERFIL_TURNO_CANDIDATO");
        Postulacion beto = suPostulacion(2L, 12L, "PERFIL_TURNO_CANDIDATO");
        Postulacion carla = suPostulacion(3L, 13L, "PRUEBA_TURNO_CANDIDATO");
        when(postulaciones.estadoBloqueandoLaFila(11L)).thenReturn("PERFIL_TURNO_CANDIDATO");
        when(postulaciones.estadoBloqueandoLaFila(12L)).thenReturn("PERFIL_TURNO_CANDIDATO");

        int cerradas = fin.alCerrarseLaVacante(VACANTE, CIERRE);

        assertThat(cerradas).isEqualTo(2);
        for (Evaluacion e : List.of(sinEmpezar, aMedias, movida)) {
            assertThat(e.getEstado()).isEqualTo("VENCIDA");
            assertThat(e.getVenceEn()).isEqualTo(CIERRE);
            verify(evaluaciones).save(e);
        }
        // Transición del sistema, sin motivo escrito y con el aviso de siempre
        verify(maquina).transicionar(ana, "CERRADA", null, null, true, false, "PLAZO_VENCIDO");
        verify(maquina).transicionar(beto, "CERRADA", null, null, true, false, "PLAZO_VENCIDO");
        verify(maquina, never()).transicionar(eq(carla), anyString(), any(), any(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean(), any());
    }

    @Test
    @DisplayName("si el equipo la movía en ese instante, manda lo que confirmó: no se cierra")
    void siLaMovieronMientrasNoSeCierra() {
        Evaluacion evaluacion = abierta(1L, "PENDIENTE");
        when(evaluaciones.sinTerminarDelPerfilDeLaVacante(VACANTE)).thenReturn(List.of(evaluacion));
        suPostulacion(1L, 11L, "PERFIL_TURNO_CANDIDATO");
        when(postulaciones.estadoBloqueandoLaFila(11L)).thenReturn("PERFIL_POR_CONFIRMAR");

        assertThat(fin.alCerrarseLaVacante(VACANTE, CIERRE)).isZero();
        assertThat(evaluacion.getEstado()).isEqualTo("VENCIDA");
        verify(maquina, never()).transicionar(any(), anyString(), any(), any(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean(), any());
    }

    @Test
    @DisplayName("quien se retiró deja su evaluación vencida y no recibe nada")
    void quienSeRetiroNoRecibeNada() {
        Evaluacion evaluacion = abierta(1L, "PENDIENTE");
        when(evaluaciones.sinTerminarDelPerfilDeLaVacante(VACANTE)).thenReturn(List.of(evaluacion));
        suPostulacion(1L, 11L, "CERRADA");

        assertThat(fin.alCerrarseLaVacante(VACANTE, CIERRE)).isZero();
        assertThat(evaluacion.getEstado()).isEqualTo("VENCIDA");
        verify(postulaciones, never()).estadoBloqueandoLaFila(any());
        verify(maquina, never()).transicionar(any(), anyString(), any(), any(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean(), any());
    }

    @Test
    @DisplayName("repetirlo no hace nada: lo ya vencido no vuelve a salir y nadie recibe dos avisos")
    void repetirloNoHaceNada() {
        when(evaluaciones.sinTerminarDelPerfilDeLaVacante(VACANTE)).thenReturn(List.of());

        assertThat(fin.alCerrarseLaVacante(VACANTE, CIERRE)).isZero();
        verify(evaluaciones, never()).save(any());
        verify(maquina, never()).transicionar(any(), anyString(), any(), any(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean(), any());
    }

    @Test
    @DisplayName("sin momento de cierre (una fila vieja), el límite es ahora")
    void sinMomentoDeCierreElLimiteEsAhora() {
        Evaluacion evaluacion = abierta(1L, "EN_CURSO");
        when(evaluaciones.sinTerminarDelPerfilDeLaVacante(VACANTE)).thenReturn(List.of(evaluacion));
        when(postulaciones.findByEvaluacionId(1L)).thenReturn(Optional.empty());

        Instant antes = Instant.now();
        fin.alCerrarseLaVacante(VACANTE, null);

        assertThat(evaluacion.getVenceEn()).isBetween(antes, Instant.now());
    }

    @Test
    @DisplayName("el sondeo recorre cada vacante cerrada con algo abierto, con el momento de su cierre; "
            + "si una falla, las demás siguen")
    void elSondeoRecorreLasCerradas() {
        Instant otroCierre = Instant.parse("2026-10-01T12:00:00Z");
        when(evaluaciones.vacantesCerradasConPerfilSinTerminar()).thenReturn(List.of(
                new Object[] {VACANTE, CIERRE}, new Object[] {41L, otroCierre}));
        when(evaluaciones.sinTerminarDelPerfilDeLaVacante(VACANTE))
                .thenThrow(new IllegalStateException("se cayó la base"));
        Evaluacion deLaOtra = abierta(5L, "PENDIENTE");
        when(evaluaciones.sinTerminarDelPerfilDeLaVacante(41L)).thenReturn(List.of(deLaOtra));
        Postulacion dora = suPostulacion(5L, 15L, "PERFIL_TURNO_CANDIDATO");
        when(postulaciones.estadoBloqueandoLaFila(15L)).thenReturn("PERFIL_TURNO_CANDIDATO");

        assertThat(fin.enLasVacantesCerradas()).isEqualTo(1);
        assertThat(deLaOtra.getVenceEn()).isEqualTo(otroCierre);
        verify(maquina).transicionar(eq(dora), eq("CERRADA"), isNull(), isNull(), eq(true), eq(false),
                eq("PLAZO_VENCIDO"));
    }
}
