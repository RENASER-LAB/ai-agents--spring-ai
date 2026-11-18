package com.renaser.ai.ai_engine.notificacion.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cómo se le dice una fecha al candidato en los avisos de «más tiempo» (V89).
 *
 * <p>Las fechas de aquí no caducan: la función recibe el «ahora» con el que decide el año, así
 * que el resultado no depende del día en que corra la prueba.
 */
@DisplayName("Una fecha dicha para el candidato")
class FechaParaElCandidatoTest {

    // 23:00 UTC del viernes 20/11/2026 son las 18:00 de Lima.
    private static final Instant VIERNES_18_LIMA = Instant.parse("2026-11-20T23:00:00Z");

    @Test
    @DisplayName("«vie 20 nov, 18:00», en hora de Lima y sin año si es el de ahora")
    void conDiaYMesEnLima() {
        assertThat(FechaParaElCandidato.conDiaYMes(VIERNES_18_LIMA, Instant.parse("2026-11-18T12:00:00Z")))
                .isEqualTo("vie 20 nov, 18:00");
    }

    @Test
    @DisplayName("con el año cuando no es el de ahora")
    void conElAnoSiCambia() {
        assertThat(FechaParaElCandidato.conDiaYMes(VIERNES_18_LIMA, Instant.parse("2025-12-31T12:00:00Z")))
                .isEqualTo("vie 20 nov 2026, 18:00");
    }

    /** A las 03:00 UTC del 1 de diciembre todavía es 30 de noviembre en Lima. */
    @Test
    @DisplayName("una hora de madrugada en UTC cae en el día anterior de Lima")
    void laMadrugadaUtcEsElDiaAnterior() {
        assertThat(FechaParaElCandidato.conDiaYMes(Instant.parse("2026-12-01T03:00:00Z"),
                Instant.parse("2026-11-18T12:00:00Z")))
                .isEqualTo("lun 30 nov, 22:00");
    }
}
