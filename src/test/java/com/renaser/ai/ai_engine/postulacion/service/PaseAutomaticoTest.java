package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;
import com.renaser.ai.ai_engine.postulacion.entity.EstadoPostulacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El pase automático, y sobre todo dónde NO se da.
 *
 * <p>Esta clase mueve candidatos sin que nadie mire, así que lo que hay que sostener no es
 * que avance —eso es lo fácil— sino que se pare en los cinco sitios donde pararse es lo
 * correcto. Cada uno de ellos, mal resuelto, le quita a alguien un turno que le habían dado
 * o le manda a una prueba que no le toca.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El pase automático a la etapa técnica")
class PaseAutomaticoTest {

    private static final Long POSTULACION = 55L;
    private static final Long VACANTE = 9L;

    @Mock private PostulacionRepository postulaciones;
    @Mock private VacanteRepository vacantes;
    @Mock private MaquinaEstados maquina;
    @Mock private EntradaEtapaTecnica entradaTecnica;
    @Mock private PuenteCalificacionIa puente;

    @InjectMocks private PaseAutomatico pase;

    private Postulacion postulacion;
    private Vacante vacante;

    @BeforeEach
    void armar() {
        postulacion = Postulacion.builder()
                .id(POSTULACION).organizacionId(1L).vacanteId(VACANTE)
                .estadoCodigo("PERFIL_POR_CONFIRMAR")
                .build();
        // Automática, sin banco y con su prueba lista: el caso en que sí toca avanzar.
        vacante = Vacante.builder()
                .id(VACANTE).organizacionId(1L)
                .calificacionAutomatica(true).aplicaEvaluacion(false)
                .build();
        lenient().when(postulaciones.findById(POSTULACION)).thenReturn(Optional.of(postulacion));
        lenient().when(vacantes.findById(VACANTE)).thenReturn(Optional.of(vacante));
        lenient().when(entradaTecnica.hayInstrumento(vacante)).thenReturn(true);
        lenient().when(maquina.siguiente("PERFIL_POR_CONFIRMAR"))
                .thenReturn(Optional.of(EstadoPostulacion.builder()
                        .codigo("PRUEBA_TURNO_CANDIDATO").build()));
    }

    @Test
    @DisplayName("con todo en su sitio, le crea la prueba y lo mueve como sistema y con motivo")
    void avanzaYDejaEscritoQueLoMovioLaVacante() {
        pase.avanzarSiToca(POSTULACION);

        verify(entradaTecnica).crearAlEntrar(postulacion, vacante);
        // El motivo escrito importa aunque sea del sistema: sin él, quien abra el historial
        // dentro de seis meses ve un salto sin autor y sin explicación.
        verify(maquina).transicionar(eq(postulacion), eq("PRUEBA_TURNO_CANDIDATO"),
                eq(null), anyString(), eq(true), eq(false), eq(null));
    }

    @Test
    @DisplayName("a quien todavía no ha entregado su banco NO se le avanza")
    void conBancoSinEntregarNoAvanza() {
        /*
          La guarda que evita el peor fallo de todo esto.

          Hay cuatro caminos que terminan en un retrato, y tres de ellos alcanzan a gente
          que sigue en su turno sin haber respondido nada: se le arma el retrato con el
          currículum a solas —el evaluador se salta porque no hay respuestas— y sin esto se
          le empuja a la prueba del puesto sin haber contestado nunca su evaluación.
        */
        vacante.setAplicaEvaluacion(true);
        when(puente.tieneEvaluacionEntregada(POSTULACION)).thenReturn(false);

        pase.avanzarSiToca(POSTULACION);

        noSeMovioNada();
    }

    @Test
    @DisplayName("con el banco ya entregado sí avanza")
    void conBancoEntregadoAvanza() {
        vacante.setAplicaEvaluacion(true);
        when(puente.tieneEvaluacionEntregada(POSTULACION)).thenReturn(true);

        pase.avanzarSiToca(POSTULACION);

        verify(entradaTecnica).crearAlEntrar(postulacion, vacante);
    }

    @Test
    @DisplayName("si la vacante no está en automático, no pasa nada")
    void laVacanteManualNoSeToca() {
        vacante.setCalificacionAutomatica(false);

        pase.avanzarSiToca(POSTULACION);

        noSeMovioNada();
    }

    @Test
    @DisplayName("a quien ya salió del perfil integral no se le arrastra")
    void aQuienYaAvanzaronNoSeLeToca() {
        // Entre que la IA empieza y termina pasan minutos: una persona puede haberlo movido
        // desde el panel. Se compara el estado EXACTO y no la etapa, porque el siguiente
        // estado se calcula a partir de este.
        postulacion.setEstadoCodigo("PRUEBA_TURNO_CANDIDATO");

        pase.avanzarSiToca(POSTULACION);

        noSeMovioNada();
    }

    @Test
    @DisplayName("sin instrumento se queda esperando a una persona, y no revienta")
    void sinInstrumentoSeQuedaEsperando() {
        // La vacante está a medio montar. Plantarse aquí daría por fallido el trabajo de la
        // IA y volvería a pagar el modelo al reintentarlo.
        when(entradaTecnica.hayInstrumento(vacante)).thenReturn(false);

        pase.avanzarSiToca(POSTULACION);

        noSeMovioNada();
    }

    @Test
    @DisplayName("una postulación que ya no existe no rompe nada")
    void laPostulacionBorradaNoRompe() {
        when(postulaciones.findById(POSTULACION)).thenReturn(Optional.empty());

        pase.avanzarSiToca(POSTULACION);

        noSeMovioNada();
    }

    @Test
    @DisplayName("una vacante que ya no existe tampoco")
    void laVacanteBorradaNoRompe() {
        when(vacantes.findById(VACANTE)).thenReturn(Optional.empty());

        pase.avanzarSiToca(POSTULACION);

        noSeMovioNada();
    }

    @Test
    @DisplayName("si no hay estado siguiente que calcular, no se inventa uno")
    void sinSiguienteNoSeAvanza() {
        when(maquina.siguiente("PERFIL_POR_CONFIRMAR")).thenReturn(Optional.empty());

        pase.avanzarSiToca(POSTULACION);

        noSeMovioNada();
    }

    private void noSeMovioNada() {
        verify(entradaTecnica, never()).crearAlEntrar(any(), any());
        verify(maquina, never()).transicionar(any(), anyString(), any(), any(),
                anyBoolean(), anyBoolean(), any());
    }
}
