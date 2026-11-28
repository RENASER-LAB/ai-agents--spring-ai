package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;
import com.renaser.ai.ai_engine.perfilintegral.service.UmbralDelPerfilIntegral;
import com.renaser.ai.ai_engine.postulacion.entity.EstadoPostulacion;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
 * que avance —eso es lo fácil— sino que se pare en los sitios donde pararse es lo correcto.
 * Cada uno de ellos, mal resuelto, le quita a alguien un turno que le habían dado o le manda
 * a una prueba que no le toca. Desde la V72, el primero de todos: la nota mínima.
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
    @Mock private UmbralDelPerfilIntegral umbral;
    @Mock private com.renaser.ai.ai_engine.postulacion.repository.EstadoPostulacionRepository estados;
    @Mock private com.renaser.ai.ai_engine.vacante.service.RecorridoDeLaVacante recorridos;
    @Mock private com.renaser.ai.ai_engine.simulacion.service.ServicioDisponibilidadSimulacion disponibilidad;

    private PaseAutomatico pase;

    /** El recorrido de la vacante de la prueba (V95): el de siempre, salvo que la prueba lo cambie. */
    private java.util.List<String> recorrido = RecorridosDePrueba.CINCO;

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
        // La fila bloqueada dice lo mismo que la entidad: nadie la movió entre medias.
        lenient().when(postulaciones.estadoBloqueandoLaFila(POSTULACION))
                .thenAnswer(inv -> postulacion.getEstadoCodigo());
        lenient().when(vacantes.findById(VACANTE)).thenReturn(Optional.of(vacante));
        lenient().when(entradaTecnica.hayInstrumento(vacante)).thenReturn(true);
        lenient().when(vacantes.findByIdAndOrganizacionId(VACANTE, 1L)).thenReturn(Optional.of(vacante));

        // La máquina calcula el siguiente de verdad, por el recorrido de la vacante (V95): con las
        // cinco, tras el Perfil viene la prueba.
        lenient().when(maquina.siguiente(anyString(), any(Recorrido.class)))
                .thenAnswer(inv -> RecorridosDePrueba.siguienteDe(inv.getArgument(0), inv.getArgument(1)));
        lenient().when(estados.findById(anyString()))
                .thenAnswer(inv -> RecorridosDePrueba.estadoDe(inv.getArgument(0)));
        lenient().when(recorridos.de(VACANTE)).thenAnswer(inv -> recorrido);
        lenient().when(recorridos.tiene(eq(VACANTE), anyString()))
                .thenAnswer(inv -> recorrido.contains(inv.<String>getArgument(1)));
        AvanceDelRecorrido avance = new AvanceDelRecorrido(maquina, estados, postulaciones, vacantes,
                recorridos, RegistroDeEtapas.con(java.util.List.of(
                        new com.renaser.ai.ai_engine.perfilintegral.service.EtapaDelPerfilIntegral(),
                        new EtapaDeLaPrueba(entradaTecnica),
                        new com.renaser.ai.ai_engine.simulacion.service.EtapaDeLaSimulacion(disponibilidad),
                        new com.renaser.ai.ai_engine.decision.service.EtapaDeLaDecision())));
        pase = new PaseAutomatico(postulaciones, vacantes, avance, puente, umbral);
        // Con su nota completa por encima de la mínima de por defecto (60)
        conNota("72.40");
    }

    private void conNota(String nota) {
        lenient().when(umbral.notaCompleta(POSTULACION))
                .thenReturn(nota == null ? Optional.empty() : Optional.of(new BigDecimal(nota)));
    }

    @Test
    @DisplayName("con 72,40 y mínima 60, le crea la prueba y lo mueve como sistema, con la nota y la mínima en el motivo (AC-02)")
    void avanzaYDejaEscritoQueLoMovioLaVacante() {
        assertThat(pase.avanzarSiToca(POSTULACION)).isTrue();

        verify(entradaTecnica).crearAlEntrar(postulacion, vacante);
        // El motivo escrito importa aunque sea del sistema: sin él, quien abra el historial
        // dentro de seis meses ve un salto sin autor y sin explicación.
        verify(maquina).transicionar(postulacion, "PRUEBA_TURNO_CANDIDATO", null,
                "Pase automático: nota del Perfil Integral 72,40 (mínima 60)", true, false, null);
    }

    @Test
    @DisplayName("con 59,99 y mínima 60 no pasa: se queda en «por confirmar», sin prueba (AC-03)")
    void conMenosDeLaMinimaNoPasa() {
        conNota("59.99");

        assertThat(pase.avanzarSiToca(POSTULACION)).isFalse();

        noSeMovioNada();
    }

    @Test
    @DisplayName("60,00 justo llega: la comparación es con sus dos decimales")
    void laMinimaJustaLlega() {
        conNota("60.00");

        assertThat(pase.avanzarSiToca(POSTULACION)).isTrue();
    }

    @Test
    @DisplayName("sin nota completa no pasa, aunque la vacante avance sola (AC-04, AC-05)")
    void sinNotaCompletaNoPasa() {
        // La IA falló, las abiertas quedaron sin nota, o solo hay la provisional de lo cerrado:
        // el umbral no da nota completa y el pase no se da.
        conNota(null);

        assertThat(pase.avanzarSiToca(POSTULACION)).isFalse();

        noSeMovioNada();
    }

    @Test
    @DisplayName("la mínima es la de la vacante: con 70, un 65 no pasa")
    void laMinimaEsLaDeLaVacante() {
        vacante.setNotaMinima(new BigDecimal("70"));
        conNota("65");

        assertThat(pase.avanzarSiToca(POSTULACION)).isFalse();

        noSeMovioNada();
    }

    @Test
    @DisplayName("con mínima 0 pasa con cualquier nota, también 0; sin nota completa, no (AC-11)")
    void conMinimaCeroPasaCualquierNota() {
        vacante.setNotaMinima(BigDecimal.ZERO);
        conNota("0");
        assertThat(pase.avanzarSiToca(POSTULACION)).isTrue();
        verify(maquina).transicionar(postulacion, "PRUEBA_TURNO_CANDIDATO", null,
                "Pase automático: nota del Perfil Integral 0,00 (mínima 0)", true, false, null);
    }

    @Test
    @DisplayName("con mínima 0 y sin nota completa, tampoco pasa (AC-11)")
    void conMinimaCeroSinNotaNoPasa() {
        vacante.setNotaMinima(BigDecimal.ZERO);
        conNota(null);

        assertThat(pase.avanzarSiToca(POSTULACION)).isFalse();

        noSeMovioNada();
    }

    @Test
    @DisplayName("tras recalcular, si ahora llega, pasa y el historial dice que fue tras recalcular (AC-06)")
    void trasRecalcularPasaConSuMotivo() {
        conNota("63.10");

        assertThat(pase.avanzarTrasRecalcular(POSTULACION)).isTrue();

        verify(entradaTecnica).crearAlEntrar(postulacion, vacante);
        verify(maquina).transicionar(postulacion, "PRUEBA_TURNO_CANDIDATO", null,
                "Pase automático tras recalcular: nota 63,10 (mínima 60)", true, false, null);
    }

    @Test
    @DisplayName("tras recalcular, si sigue sin llegar, no pasa")
    void trasRecalcularSinLlegarNoPasa() {
        conNota("55");

        assertThat(pase.avanzarTrasRecalcular(POSTULACION)).isFalse();

        noSeMovioNada();
    }

    @Test
    @DisplayName("a quien ya está en la prueba no se le toca aunque su nota baje (AC-06)")
    void enLaPruebaNoSeTocaAunqueBaje() {
        postulacion.setEstadoCodigo("PRUEBA_TURNO_CANDIDATO");
        conNota("40");

        assertThat(pase.avanzarTrasRecalcular(POSTULACION)).isFalse();

        noSeMovioNada();
    }

    @Test
    @DisplayName("ninguna nota lleva a «No continúa»: el pase solo sabe ir a la prueba (AC-13)")
    void ningunaNotaDescarta() {
        for (String nota : new String[] {"0", "12.5", "59.99"}) {
            conNota(nota);
            pase.avanzarSiToca(POSTULACION);
            pase.avanzarTrasRecalcular(POSTULACION);
        }
        conNota(null);
        pase.avanzarSiToca(POSTULACION);

        verify(maquina, never()).transicionar(any(), eq("NO_CONTINUA"), any(), any(),
                anyBoolean(), anyBoolean(), any());
        noSeMovioNada();
    }

    @Test
    @DisplayName("si una persona la movió a la vez, al bloquear la fila se ve y no se toca (dos recálculos seguidos)")
    void siLaMovieronALaVezNoSeToca() {
        when(postulaciones.estadoBloqueandoLaFila(POSTULACION)).thenReturn("PRUEBA_TURNO_CANDIDATO");

        assertThat(pase.avanzarTrasRecalcular(POSTULACION)).isFalse();

        noSeMovioNada();
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
        when(maquina.siguiente(eq("PERFIL_POR_CONFIRMAR"), any(Recorrido.class))).thenReturn(Optional.empty());

        pase.avanzarSiToca(POSTULACION);

        noSeMovioNada();
    }

    // ============ El recorrido de la vacante (V95) ============

    @Test
    @DisplayName("Perfil → Decisión: pasa sola a «Decisión · por confirmar» y no se crea ninguna prueba (AC-07)")
    void sinPruebaPasaALaDecision() {
        recorrido = java.util.List.of("PERFIL_INTEGRAL", "DECISION");
        // Ni siquiera hace falta tener prueba: la vacante no la tiene.
        lenient().when(entradaTecnica.hayInstrumento(vacante)).thenReturn(false);

        assertThat(pase.avanzarSiToca(POSTULACION)).isTrue();

        verify(maquina).transicionar(postulacion, "DECISION_POR_CONFIRMAR", null,
                "Pase automático: nota del Perfil Integral 72,40 (mínima 60)", true, false, null);
        verify(entradaTecnica, never()).crearAlEntrar(any(), any());
    }

    @Test
    @DisplayName("Perfil → Simulación: pasa a «Simulación · por habilitar» y se miran las sesiones")
    void conLaSimulacionDespuesDelPerfil() {
        recorrido = java.util.List.of("PERFIL_INTEGRAL", "SIMULACION", "PRUEBA_PUESTO", "DECISION");

        assertThat(pase.avanzarSiToca(POSTULACION)).isTrue();

        verify(maquina).transicionar(postulacion, "SIMULACION_POR_HABILITAR", null,
                "Pase automático: nota del Perfil Integral 72,40 (mínima 60)", true, false, null);
        verify(disponibilidad).recalcularVacante(1L, VACANTE);
        verify(entradaTecnica, never()).crearAlEntrar(any(), any());
    }

    @Test
    @DisplayName("si el Perfil Integral es la última etapa, no mueve a nadie")
    void soloElPerfilNoMueve() {
        recorrido = java.util.List.of("PERFIL_INTEGRAL");

        assertThat(pase.avanzarSiToca(POSTULACION)).isFalse();

        noSeMovioNada();
    }

    @Test
    @DisplayName("una vacante archivada o eliminada no mueve a nadie, aunque la nota llegue")
    void archivadaOEliminadaNo() {
        vacante.setArchivadaEn(java.time.Instant.now());
        assertThat(pase.avanzarSiToca(POSTULACION)).isFalse();

        vacante.setArchivadaEn(null);
        vacante.setEliminadaEn(java.time.Instant.now());
        assertThat(pase.avanzarTrasRecalcular(POSTULACION)).isFalse();

        noSeMovioNada();
    }

    private void noSeMovioNada() {
        verify(entradaTecnica, never()).crearAlEntrar(any(), any());
        verify(maquina, never()).transicionar(any(), anyString(), any(), any(),
                anyBoolean(), anyBoolean(), any());
    }
}
