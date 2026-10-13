package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.ai.service.ColaCalificacionIa;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaEtapa;
import com.renaser.ai.ai_engine.perfilintegral.entity.PerfilTalento;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaEtapaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PerfilTalentoRepository;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * La nota mínima del Perfil Integral (V72): qué nota cuenta, cuándo llega y qué dice la ficha.
 *
 * <p>La usan el pase automático y la ficha del panel; si esto se equivoca, se equivocan los dos
 * a la vez, que es justo para lo que vive en un solo sitio.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("La nota mínima del Perfil Integral")
class UmbralDelPerfilIntegralTest {

    private static final Long POSTULACION = 31L;
    private static final Instant RETRATO = Instant.parse("2026-10-12T15:00:00Z");

    @Mock private PerfilTalentoRepository perfiles;
    @Mock private NotaEtapaRepository notasEtapa;
    @Mock private PuenteCalificacionIa puente;
    @Mock private ColaCalificacionIa cola;

    @InjectMocks private UmbralDelPerfilIntegral umbral;

    private void conRetrato() {
        when(perfiles.findByPostulacionId(POSTULACION)).thenReturn(Optional.of(
                PerfilTalento.builder().postulacionId(POSTULACION).actualizadoEn(RETRATO).build()));
    }

    private void conNota(String puntaje, Instant calculadaEn) {
        lenient().when(notasEtapa.findByPostulacionIdAndEtapaCodigo(POSTULACION, "PERFIL_INTEGRAL"))
                .thenReturn(Optional.of(NotaEtapa.builder().postulacionId(POSTULACION)
                        .etapaCodigo("PERFIL_INTEGRAL").puntaje(new BigDecimal(puntaje))
                        .calculadaEn(calculadaEn).build()));
    }

    @Nested
    @DisplayName("La nota que cuenta")
    class LaNotaQueCuenta {

        @Test
        @DisplayName("la calculada en el cierre, con el retrato ya hecho, cuenta")
        void laDelCierreCuenta() {
            conRetrato();
            conNota("72.40", RETRATO.plusMillis(3));

            assertThat(umbral.notaCompleta(POSTULACION)).contains(new BigDecimal("72.40"));
        }

        @Test
        @DisplayName("la del mismo instante que el retrato también cuenta (la base redondea a microsegundos)")
        void laDelMismoInstanteCuenta() {
            conRetrato();
            conNota("60.00", RETRATO);

            assertThat(umbral.notaCompleta(POSTULACION)).contains(new BigDecimal("60.00"));
        }

        @Test
        @DisplayName("la provisional de lo cerrado, anterior al retrato, no cuenta (AC-05)")
        void laProvisionalNoCuenta() {
            // Un banco clásico escribe la nota de lo cerrado al entregar; si el cierre después no
            // la recalcula —pesos que no pesan nada que se pueda llenar—, queda anterior al retrato.
            conRetrato();
            conNota("90", RETRATO.minus(Duration.ofMinutes(4)));

            assertThat(umbral.notaCompleta(POSTULACION)).isEmpty();
        }

        @Test
        @DisplayName("sin retrato no hay nota completa, aunque haya nota (la IA no cerró)")
        void sinRetratoNoHay() {
            when(perfiles.findByPostulacionId(POSTULACION)).thenReturn(Optional.empty());
            conNota("90", RETRATO);

            assertThat(umbral.notaCompleta(POSTULACION)).isEmpty();
        }

        @Test
        @DisplayName("con el retrato hecho y sin fila de nota —abiertas sin nota—, tampoco (AC-04)")
        void sinFilaNoHay() {
            conRetrato();
            when(notasEtapa.findByPostulacionIdAndEtapaCodigo(POSTULACION, "PERFIL_INTEGRAL"))
                    .thenReturn(Optional.empty());

            assertThat(umbral.notaCompleta(POSTULACION)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Cuándo llega y cómo se dice")
    class CuandoLlega {

        @Test
        @DisplayName("llega con la mínima o más, con sus dos decimales: 59,99 no y 60,00 sí")
        void llegaConDosDecimales() {
            assertThat(UmbralDelPerfilIntegral.llega(new BigDecimal("59.99"), 60)).isFalse();
            assertThat(UmbralDelPerfilIntegral.llega(new BigDecimal("60.00"), 60)).isTrue();
            assertThat(UmbralDelPerfilIntegral.llega(new BigDecimal("72.40"), 60)).isTrue();
            assertThat(UmbralDelPerfilIntegral.llega(BigDecimal.ZERO, 0)).isTrue();
        }

        @Test
        @DisplayName("la mínima es la de la vacante; sin fijar, 60")
        void laMinimaDeLaVacante() {
            assertThat(UmbralDelPerfilIntegral.minimaDe(Vacante.builder().build())).isEqualTo(60);
            assertThat(UmbralDelPerfilIntegral.minimaDe(Vacante.builder()
                    .notaMinima(new BigDecimal("75")).build())).isEqualTo(75);
            Vacante sinFijar = Vacante.builder().build();
            sinFijar.setNotaMinima(null);
            assertThat(UmbralDelPerfilIntegral.minimaDe(sinFijar)).isEqualTo(60);
        }

        @Test
        @DisplayName("los motivos del historial llevan la nota con coma y la mínima")
        void losMotivos() {
            assertThat(UmbralDelPerfilIntegral.motivoAlTerminar(new BigDecimal("72.4"), 60))
                    .isEqualTo("Pase automático: nota del Perfil Integral 72,40 (mínima 60)");
            assertThat(UmbralDelPerfilIntegral.motivoTrasRecalcular(new BigDecimal("63.1"), 60))
                    .isEqualTo("Pase automático tras recalcular: nota 63,10 (mínima 60)");
        }
    }

    @Nested
    @DisplayName("Lo que explica la ficha")
    class LoQueExplicaLaFicha {

        private Postulacion postulacion;
        private Vacante vacante;

        @BeforeEach
        void armar() {
            postulacion = Postulacion.builder().id(POSTULACION).estadoCodigo("PERFIL_POR_CONFIRMAR").build();
            vacante = Vacante.builder().id(9L).calificacionAutomatica(true).aplicaEvaluacion(true).build();
            lenient().when(puente.tieneEvaluacionEntregada(POSTULACION)).thenReturn(true);
            lenient().when(cola.comoVa(POSTULACION)).thenReturn("TERMINADA");
        }

        @Test
        @DisplayName("con 58,20 y mínima 60: no llega, con la nota y la mínima (AC-03)")
        void noLlega() {
            conRetrato();
            conNota("58.2", RETRATO.plusSeconds(1));

            assertThat(umbral.porQueEspera(postulacion, vacante))
                    .isEqualTo("Nota 58,20: no llega a la mínima de la vacante (60)");
        }

        @Test
        @DisplayName("sin nota completa: que se revise el Perfil Integral (AC-04)")
        void sinNotaCompleta() {
            when(perfiles.findByPostulacionId(POSTULACION)).thenReturn(Optional.empty());

            assertThat(umbral.porQueEspera(postulacion, vacante))
                    .isEqualTo("Sin nota completa: revisa el Perfil Integral");
        }

        @Test
        @DisplayName("si llega, no hay nada que explicar sobre la nota")
        void siLlegaNada() {
            conRetrato();
            conNota("75", RETRATO.plusSeconds(1));

            assertThat(umbral.porQueEspera(postulacion, vacante)).isNull();
        }

        @Test
        @DisplayName("con el interruptor apagado no se explica nada: la mínima no aplica (AC-08)")
        void conElInterruptorApagado() {
            vacante.setCalificacionAutomatica(false);

            assertThat(umbral.porQueEspera(postulacion, vacante)).isNull();
        }

        @Test
        @DisplayName("fuera de «por confirmar» no se explica nada")
        void fueraDePorConfirmar() {
            postulacion.setEstadoCodigo("PRUEBA_TURNO_CANDIDATO");
            assertThat(umbral.porQueEspera(postulacion, vacante)).isNull();

            postulacion.setEstadoCodigo("PERFIL_CALIFICANDO");
            assertThat(umbral.porQueEspera(postulacion, vacante)).isNull();
        }

        @Test
        @DisplayName("con el banco sin entregar no es la nota la que la para")
        void sinBancoEntregado() {
            when(puente.tieneEvaluacionEntregada(POSTULACION)).thenReturn(false);

            assertThat(umbral.porQueEspera(postulacion, vacante)).isNull();
        }

        @Test
        @DisplayName("con la IA trabajando no se contradice a «nota en camino»")
        void conLaIaTrabajando() {
            when(cola.comoVa(POSTULACION)).thenReturn("EN_CURSO");

            assertThat(umbral.porQueEspera(postulacion, vacante)).isNull();
        }

        @Test
        @DisplayName("sin vacante no hay nada que explicar")
        void sinVacante() {
            assertThat(umbral.porQueEspera(postulacion, null)).isNull();
        }
    }
}
