package com.renaser.ai.ai_engine.vacante.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El día en que se cierra sola una vacante y lo que se le exige (V78).
 *
 * <p>Lo que se protege es la hora de Lima: el panel elige un día y la vacante tiene que
 * cerrarse a las 23:59:59 de ese día en Lima, no a las 19:00 del anterior (la medianoche UTC)
 * ni a las 23:59:59 UTC (las 18:59 de Lima). Los casos de borde son el día 1 y la medianoche.
 */
@DisplayName("La fecha límite de la vacante, en hora de Lima")
class FormaDeCierreTest {

    @Test
    @DisplayName("la medianoche UTC de un día es ese día: el 31/10 cierra el 31/10 a las 23:59:59 de Lima")
    void laMedianocheUtcEsEseDia() {
        assertThat(FormaDeCierre.alFinalDelDia(Instant.parse("2026-10-31T00:00:00Z")))
                .isEqualTo(Instant.parse("2026-11-01T04:59:59Z"));
    }

    @Test
    @DisplayName("el día 1 no cae en el mes anterior")
    void elDiaUnoNoCaeEnElMesAnterior() {
        // La medianoche UTC del 1 de diciembre, que en Lima son las 19:00 del 30 de noviembre
        assertThat(FormaDeCierre.alFinalDelDia(Instant.parse("2026-12-01T00:00:00Z")))
                .isEqualTo(Instant.parse("2026-12-02T04:59:59Z"));
        // Y el 1 de enero no cae en el año anterior
        assertThat(FormaDeCierre.alFinalDelDia(Instant.parse("2027-01-01T00:00:00Z")))
                .isEqualTo(Instant.parse("2027-01-02T04:59:59Z"));
    }

    @Test
    @DisplayName("cerca de la medianoche de Lima manda el día de Lima, no el de UTC")
    void cercaDeLaMedianocheMandaLima() {
        // 23:30 del 31/10 en Lima = 04:30 del 01/11 en UTC: sigue siendo el 31/10
        assertThat(FormaDeCierre.alFinalDelDia(Instant.parse("2026-11-01T04:30:00Z")))
                .isEqualTo(Instant.parse("2026-11-01T04:59:59Z"));
        // 00:10 del 01/11 en Lima: ya es el 01/11
        assertThat(FormaDeCierre.alFinalDelDia(Instant.parse("2026-11-01T05:10:00Z")))
                .isEqualTo(Instant.parse("2026-11-02T04:59:59Z"));
    }

    @Test
    @DisplayName("aplicada dos veces da lo mismo: guardar la misma fecha no es un cambio")
    void esIdempotente() {
        Instant una = FormaDeCierre.alFinalDelDia(Instant.parse("2026-10-31T00:00:00Z"));
        assertThat(FormaDeCierre.alFinalDelDia(una)).isEqualTo(una);
        assertThat(FormaDeCierre.alFinalDelDia(null)).isNull();
    }

    @Test
    @DisplayName("Permanente no pide nada; Fecha límite pide un día que no haya pasado en Lima")
    void loQueSeExige() {
        // Las 22:00 del 31/10 en Lima
        Instant ahora = Instant.parse("2026-11-01T03:00:00Z");

        assertThat(FormaDeCierre.falta("PERMANENTE", null, ahora)).isNull();
        assertThat(FormaDeCierre.falta(" FECHA ", null, ahora)).isEqualTo(FormaDeCierre.SIN_DIA);
        // Hoy, en Lima, vale: se cierra esta noche
        assertThat(FormaDeCierre.falta("FECHA", Instant.parse("2026-10-31T00:00:00Z"), ahora)).isNull();
        // Ayer, en Lima, ya pasó
        assertThat(FormaDeCierre.falta("FECHA", Instant.parse("2026-10-30T00:00:00Z"), ahora))
                .isEqualTo(FormaDeCierre.DIA_PASADO);
        assertThat(FormaDeCierre.falta("PLAZAS", null, ahora)).isEqualTo(FormaDeCierre.FORMA_DESCONOCIDA);
        assertThat(FormaDeCierre.falta(null, null, ahora)).isEqualTo(FormaDeCierre.FORMA_DESCONOCIDA);
    }

    @Test
    @DisplayName("pasada la medianoche de Lima, el día anterior ya no vale aunque en UTC siga")
    void pasadaLaMedianocheDeLima() {
        // 00:30 del 01/12 en Lima = 05:30 UTC: el 30/11 ya pasó
        Instant ahora = Instant.parse("2026-12-01T05:30:00Z");
        assertThat(FormaDeCierre.faltaDeLaFecha(Instant.parse("2026-11-30T00:00:00Z"), ahora))
                .isEqualTo(FormaDeCierre.DIA_PASADO);
        assertThat(FormaDeCierre.faltaDeLaFecha(Instant.parse("2026-12-01T00:00:00Z"), ahora)).isNull();
    }

    @Test
    @DisplayName("solo la de fecha límite se cierra sola")
    void soloLaDeFechaSeCierraSola() {
        assertThat(FormaDeCierre.esPorFecha("FECHA")).isTrue();
        assertThat(FormaDeCierre.esPorFecha("PERMANENTE")).isFalse();
        assertThat(FormaDeCierre.esPorFecha(null)).isFalse();
    }
}
