package com.renaser.ai.ai_engine.colaborador.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Los estados del colaborador salen de las fechas, en hora de Lima")
class EstadoLaboralTest {

    @Test
    @DisplayName("por ingresar hasta el día del ingreso, activo desde ese día, sin que nadie toque nada")
    void porIngresarYActivo() {
        LocalDate ingreso = LocalDate.now().plusDays(3);
        assertThat(EstadoLaboral.de(ingreso, null, ingreso.minusDays(1))).isEqualTo(EstadoLaboral.POR_INGRESAR);
        assertThat(EstadoLaboral.de(ingreso, null, ingreso)).isEqualTo(EstadoLaboral.ACTIVO);
    }

    @Test
    @DisplayName("un cese con fecha de hoy deja a la persona activa hasta el final del día")
    void elCeseDeHoy() {
        LocalDate hoy = LocalDate.now();
        LocalDate ingreso = hoy.minusYears(1);
        assertThat(EstadoLaboral.de(ingreso, hoy, hoy)).isEqualTo(EstadoLaboral.ACTIVO);
        assertThat(EstadoLaboral.de(ingreso, hoy, hoy.plusDays(1))).isEqualTo(EstadoLaboral.CESADO);
        assertThat(EstadoLaboral.CESADO.puedeSerJefe()).isFalse();
        assertThat(EstadoLaboral.POR_INGRESAR.puedeSerJefe()).isTrue();
    }

    @Test
    @DisplayName("a las 20:00 de Lima todavía es hoy en Lima, aunque en UTC ya sea mañana")
    void laMedianocheDeLima() {
        LocalDate dia = LocalDate.now().minusDays(10);
        // 20:00 en Lima son las 01:00 del día siguiente en UTC.
        Instant veinteEnLima = dia.plusDays(1).atTime(1, 0).toInstant(ZoneOffset.UTC);
        HoyEnLima reloj = new HoyEnLima(Clock.fixed(veinteEnLima, ZoneOffset.UTC));
        assertThat(reloj.hoy()).isEqualTo(dia);
    }
}
