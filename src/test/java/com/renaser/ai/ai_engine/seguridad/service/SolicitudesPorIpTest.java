package com.renaser.ai.ai_engine.seguridad.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El tope por IP de las solicitudes de contraseña nueva: el que frena a quien prueba miles
 * de correos distintos desde la misma máquina, que el tope por cuenta no ve.
 */
@DisplayName("El tope por IP de las solicitudes de contraseña nueva")
class SolicitudesPorIpTest {

    /** Un reloj que se mueve a mano: las pruebas no esperan una hora de verdad. */
    private static final class RelojAMano extends Clock {
        private Instant ahora = Instant.now();

        void avanzar(Duration cuanto) {
            ahora = ahora.plus(cuanto);
        }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zona) { return this; }
        @Override public Instant instant() { return ahora; }
    }

    private final RelojAMano reloj = new RelojAMano();
    private final SolicitudesPorIp tope = new SolicitudesPorIp(reloj);

    @Test
    @DisplayName("admite hasta el máximo en la hora y rechaza la siguiente")
    void admiteHastaElMaximo() {
        assertThat(tope.admitir("10.0.0.1", 3)).isTrue();
        assertThat(tope.admitir("10.0.0.1", 3)).isTrue();
        assertThat(tope.admitir("10.0.0.1", 3)).isTrue();
        assertThat(tope.admitir("10.0.0.1", 3)).isFalse();
        assertThat(tope.admitir("10.0.0.1", 3)).isFalse();
    }

    @Test
    @DisplayName("cada IP lleva su propia cuenta")
    void cadaIpLlevaSuCuenta() {
        assertThat(tope.admitir("10.0.0.1", 1)).isTrue();
        assertThat(tope.admitir("10.0.0.1", 1)).isFalse();
        assertThat(tope.admitir("10.0.0.2", 1)).isTrue();
    }

    @Test
    @DisplayName("cumplida la hora, la cuenta vuelve a empezar")
    void cumplidaLaHoraVuelveAEmpezar() {
        assertThat(tope.admitir("10.0.0.1", 1)).isTrue();
        reloj.avanzar(Duration.ofMinutes(59));
        assertThat(tope.admitir("10.0.0.1", 1)).isFalse();
        reloj.avanzar(Duration.ofMinutes(1));
        assertThat(tope.admitir("10.0.0.1", 1)).isTrue();
    }

    @Test
    @DisplayName("la que se rechaza no suma: insistir no alarga la espera")
    void laRechazadaNoSuma() {
        assertThat(tope.admitir("10.0.0.1", 1)).isTrue();
        for (int i = 0; i < 10; i++) {
            assertThat(tope.admitir("10.0.0.1", 1)).isFalse();
        }
        reloj.avanzar(Duration.ofHours(1));
        assertThat(tope.admitir("10.0.0.1", 1)).isTrue();
    }

    @Test
    @DisplayName("un tope de cero no admite nada, y una IP desconocida cuenta como una sola")
    void topeCeroYSinIp() {
        assertThat(tope.admitir("10.0.0.1", 0)).isFalse();
        assertThat(tope.admitir(null, 1)).isTrue();
        assertThat(tope.admitir(null, 1)).isFalse();
    }
}
