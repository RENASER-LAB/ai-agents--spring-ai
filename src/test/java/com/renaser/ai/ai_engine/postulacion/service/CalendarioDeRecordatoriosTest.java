package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.notificacion.service.FechaParaElCandidato;
import com.renaser.ai.ai_engine.postulacion.service.CalendarioDeRecordatorios.Cual;
import com.renaser.ai.ai_engine.postulacion.service.CalendarioDeRecordatorios.Toca;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cuándo sale cada recordatorio (V70, AC-13 a AC-17), con el reloj puesto a mano.
 *
 * <p>Nada de esperar 24 horas ni de depender de a qué hora corre la prueba: cada caso dice la
 * hora de Lima en la que mira. La semana es la siguiente a hoy, para no escribir fechas que
 * caduquen.
 */
@DisplayName("El calendario de los recordatorios")
class CalendarioDeRecordatoriosTest {

    private static final LocalDate LUNES = LocalDate.now(FechaParaElCandidato.LIMA)
            .with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    private static final Duration H24 = Duration.ofHours(24);

    /** Un momento de esa semana en hora de Lima: el día (0 = lunes), la hora y el minuto. */
    private static Instant lima(int dia, int hora, int minuto) {
        return LUNES.plusDays(dia).atTime(hora, minuto).atZone(FechaParaElCandidato.LIMA).toInstant();
    }

    private static Optional<Toca> toca(Instant turno, Instant vence, Instant ahora,
                                       boolean yaElPrimero, boolean yaElDelPlazo) {
        return CalendarioDeRecordatorios.queToca(turno, vence, ahora, H24, H24, yaElPrimero, yaElDelPlazo);
    }

    // ============ La franja de Lima ============

    @Test
    @DisplayName("de 8:00 a 21:00 de Lima sí; fuera de ahí, no")
    void laFranja() {
        assertThat(CalendarioDeRecordatorios.enHorario(lima(1, 7, 59))).isFalse();
        assertThat(CalendarioDeRecordatorios.enHorario(lima(1, 8, 0))).isTrue();
        assertThat(CalendarioDeRecordatorios.enHorario(lima(1, 20, 59))).isTrue();
        assertThat(CalendarioDeRecordatorios.enHorario(lima(1, 21, 0))).isFalse();
    }

    @Test
    @DisplayName("lo que caía de noche sale a las 8:00; lo del día se queda donde estaba")
    void deNocheALasOcho() {
        assertThat(CalendarioDeRecordatorios.enFranja(lima(1, 22, 0))).isEqualTo(lima(2, 8, 0));
        assertThat(CalendarioDeRecordatorios.enFranja(lima(1, 21, 0))).isEqualTo(lima(2, 8, 0));
        assertThat(CalendarioDeRecordatorios.enFranja(lima(1, 3, 0))).isEqualTo(lima(1, 8, 0));
        assertThat(CalendarioDeRecordatorios.enFranja(lima(1, 15, 30))).isEqualTo(lima(1, 15, 30));
    }

    // ============ El de las 24 horas (AC-13, AC-15) ============

    @Test
    @DisplayName("a las 24 horas de entrar sale el primero, ni un minuto antes, y una sola vez")
    void alas24Horas() {
        Instant turno = lima(0, 10, 0);
        assertThat(toca(turno, null, lima(1, 9, 59), false, false)).isEmpty();
        assertThat(toca(turno, null, lima(1, 10, 0), false, false))
                .contains(new Toca(Cual.TRAS_ENTRAR, false));
        assertThat(toca(turno, null, lima(1, 15, 0), true, false))
                .as("ya salió: no se repite").isEmpty();
    }

    @Test
    @DisplayName("si tocaba de noche, no sale de noche: sale a las 8:00 (AC-17)")
    void elDeLaNocheSaleALasOcho() {
        Instant turno = lima(0, 22, 30);
        assertThat(toca(turno, null, lima(1, 22, 30), false, false)).isEmpty();
        assertThat(toca(turno, null, lima(2, 7, 59), false, false)).isEmpty();
        assertThat(toca(turno, null, lima(2, 8, 0), false, false))
                .contains(new Toca(Cual.TRAS_ENTRAR, false));
    }

    @Test
    @DisplayName("las horas son un parámetro: con 48, el primero sale a los dos días")
    void lasHorasSonParametro() {
        Instant turno = lima(0, 10, 0);
        assertThat(CalendarioDeRecordatorios.queToca(turno, null, lima(1, 10, 0),
                Duration.ofHours(48), H24, false, false)).isEmpty();
        assertThat(CalendarioDeRecordatorios.queToca(turno, null, lima(2, 10, 0),
                Duration.ofHours(48), H24, false, false)).contains(new Toca(Cual.TRAS_ENTRAR, false));
    }

    // ============ El del plazo (AC-14, AC-15) ============

    @Test
    @DisplayName("24 horas antes de vencer sale el del plazo, aunque ya saliera el primero")
    void antesDelPlazo() {
        Instant turno = lima(0, 10, 0);
        Instant vence = lima(4, 18, 0);
        assertThat(toca(turno, vence, lima(3, 17, 59), true, false)).isEmpty();
        assertThat(toca(turno, vence, lima(3, 18, 0), true, false))
                .contains(new Toca(Cual.ANTES_DEL_PLAZO, false));
        assertThat(toca(turno, vence, lima(3, 19, 0), true, true))
                .as("ya salió para esta fecha").isEmpty();
    }

    @Test
    @DisplayName("si la fecha se mueve después del recordatorio, sale una vez para la nueva")
    void laFechaNuevaTieneElSuyo() {
        Instant turno = lima(0, 10, 0);
        Instant nueva = lima(5, 18, 0);
        // «yaElDelPlazo» es por fecha: para la nueva todavía no salió
        assertThat(toca(turno, nueva, lima(4, 18, 0), true, false))
                .contains(new Toca(Cual.ANTES_DEL_PLAZO, false));
    }

    @Test
    @DisplayName("el del plazo que caía de noche sale a las 8:00, antes de que venza")
    void elDelPlazoDeNoche() {
        Instant turno = lima(0, 10, 0);
        Instant vence = lima(4, 23, 59);
        // 24 horas antes son las 23:59 del jueves: sale el viernes a las 8:00
        assertThat(toca(turno, vence, lima(3, 23, 59), true, false)).isEmpty();
        assertThat(toca(turno, vence, lima(4, 8, 0), true, false))
                .contains(new Toca(Cual.ANTES_DEL_PLAZO, false));
    }

    // ============ Los dos juntos (AC-17) ============

    @Test
    @DisplayName("a menos de 12 horas el uno del otro, solo sale el del plazo")
    void demasiadoCercaSoloElDelPlazo() {
        Instant turno = lima(0, 10, 0);
        Instant vence = lima(2, 6, 0);
        // El del plazo: martes 6:00 → 8:00. El primero: martes 10:00. Dos horas: solo el del plazo
        assertThat(toca(turno, vence, lima(1, 8, 0), false, false))
                .contains(new Toca(Cual.ANTES_DEL_PLAZO, true));
        // El primero quedó gastado al salir el del plazo, y ya no sale
        assertThat(toca(turno, vence, lima(1, 10, 0), true, true)).isEmpty();
    }

    @Test
    @DisplayName("cerca y antes del del plazo, el primero espera y no sale solo")
    void elPrimeroNoSeAdelanta() {
        Instant turno = lima(0, 10, 0);
        Instant vence = lima(2, 18, 0);
        // El primero: martes 10:00. El del plazo: martes 18:00. Ocho horas: solo el del plazo
        assertThat(toca(turno, vence, lima(1, 10, 0), false, false)).isEmpty();
        assertThat(toca(turno, vence, lima(1, 18, 0), false, false))
                .contains(new Toca(Cual.ANTES_DEL_PLAZO, true));
    }

    @Test
    @DisplayName("si el del plazo sale antes que el primero y a 12 horas o más, salen los dos (AC-15)")
    void despuesDelDelPlazoTambienElPrimero() {
        Instant turno = lima(0, 10, 0);
        Instant vence = lima(1, 20, 0);
        // Al entrar quedaban 34 horas: el del plazo sale el lunes 20:00 y el primero el martes
        // 10:00, catorce horas después. Lejos: el del plazo no gasta al primero
        assertThat(toca(turno, vence, lima(0, 20, 0), false, false))
                .contains(new Toca(Cual.ANTES_DEL_PLAZO, false));
        assertThat(toca(turno, vence, lima(1, 9, 59), false, true)).isEmpty();
        assertThat(toca(turno, vence, lima(1, 10, 0), false, true))
                .contains(new Toca(Cual.TRAS_ENTRAR, false));
        assertThat(toca(turno, vence, lima(1, 15, 0), true, true))
                .as("los dos salieron: no se repite ninguno").isEmpty();
    }

    @Test
    @DisplayName("justo a 12 horas, primero el de las 24 y luego el del plazo: salen los dos")
    void aDoceHorasConElPrimeroDelanteSalenLosDos() {
        Instant turno = lima(0, 8, 30);
        Instant vence = lima(2, 20, 30);
        // El primero: martes 8:30. El del plazo: martes 20:30. Doce horas justas: no es «a menos»
        assertThat(toca(turno, vence, lima(1, 8, 30), false, false))
                .contains(new Toca(Cual.TRAS_ENTRAR, false));
        assertThat(toca(turno, vence, lima(1, 20, 30), true, false))
                .contains(new Toca(Cual.ANTES_DEL_PLAZO, false));
    }

    @Test
    @DisplayName("a 11:59 horas, con el primero delante: solo sale el del plazo")
    void aOnceHorasConElPrimeroDelanteSoloElDelPlazo() {
        Instant turno = lima(0, 8, 30);
        Instant vence = lima(2, 20, 29);
        assertThat(toca(turno, vence, lima(1, 8, 30), false, false)).isEmpty();
        assertThat(toca(turno, vence, lima(1, 20, 29), false, false))
                .contains(new Toca(Cual.ANTES_DEL_PLAZO, true));
    }

    @Test
    @DisplayName("justo a 12 horas, con el del plazo delante: salen los dos")
    void aDoceHorasConElDelPlazoDelanteSalenLosDos() {
        Instant turno = lima(0, 20, 0);
        Instant vence = lima(2, 8, 0);
        // El del plazo: martes 8:00. El primero: martes 20:00. Doce horas justas
        assertThat(toca(turno, vence, lima(1, 8, 0), false, false))
                .contains(new Toca(Cual.ANTES_DEL_PLAZO, false));
        assertThat(toca(turno, vence, lima(1, 20, 0), false, true))
                .contains(new Toca(Cual.TRAS_ENTRAR, false));
    }

    @Test
    @DisplayName("a 11:59 horas, con el del plazo delante: el del plazo gasta al primero")
    void aOnceHorasConElDelPlazoDelanteSoloElDelPlazo() {
        Instant turno = lima(0, 20, 0);
        Instant vence = lima(2, 8, 1);
        // El del plazo: martes 8:01. El primero: martes 20:00. Once horas y 59 minutos
        assertThat(toca(turno, vence, lima(1, 8, 1), false, false))
                .contains(new Toca(Cual.ANTES_DEL_PLAZO, true));
        // Aunque no se hubiera registrado como gastado, el primero tampoco saldría
        assertThat(toca(turno, vence, lima(1, 20, 0), false, true)).isEmpty();
    }

    @Test
    @DisplayName("si el primero caería después de vencer, el del plazo lo da por gastado")
    void elPrimeroDespuesDeVencerQuedaGastado() {
        // 48 horas para el primero y 24 para el del plazo: el primero caería con el turno cerrado
        Instant turno = lima(0, 10, 0);
        Instant vence = lima(1, 18, 0);
        assertThat(CalendarioDeRecordatorios.queToca(turno, vence, lima(0, 18, 0),
                Duration.ofHours(48), H24, false, false)).contains(new Toca(Cual.ANTES_DEL_PLAZO, true));
    }

    // ============ Cuando no hay nada que recordar ============

    @Test
    @DisplayName("si al entrar ya quedaban menos de 24 horas, no hay recordatorio de plazo ni primero")
    void plazoCasiVencidoAlEntrar() {
        Instant turno = lima(0, 10, 0);
        Instant vence = lima(0, 20, 0);
        assertThat(toca(turno, vence, lima(0, 12, 0), false, false)).isEmpty();
        assertThat(toca(turno, vence, lima(0, 19, 59), false, false)).isEmpty();
    }

    @Test
    @DisplayName("vencido, nada: eso lo cierra el sondeo")
    void vencidoNada() {
        Instant turno = lima(0, 10, 0);
        Instant vence = lima(2, 12, 0);
        assertThat(toca(turno, vence, lima(2, 12, 0), false, false)).isEmpty();
        assertThat(toca(turno, vence, lima(3, 9, 0), false, false)).isEmpty();
    }

    @Test
    @DisplayName("de noche no sale ninguno, aunque los dos estén pendientes")
    void deNocheNinguno() {
        Instant turno = lima(0, 10, 0);
        Instant vence = lima(4, 18, 0);
        assertThat(toca(turno, vence, lima(3, 22, 0), false, false)).isEmpty();
        assertThat(toca(turno, vence, lima(4, 6, 0), false, false)).isEmpty();
    }
}
