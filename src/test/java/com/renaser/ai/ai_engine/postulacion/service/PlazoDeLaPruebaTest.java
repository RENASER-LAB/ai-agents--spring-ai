package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.notificacion.service.FechaParaElCandidato;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo que dice {@code {{plazo}}} en el aviso de la prueba (V70, AC-21), fila por fila.
 *
 * <p>La fecha se arma en hora de Lima a partir de hoy —el próximo viernes a las 23:59—, sin
 * fechas escritas a mano: solo importa cómo se dice, no qué día es.
 */
@DisplayName("El plazo del aviso de la prueba")
class PlazoDeLaPruebaTest {

    /** El próximo viernes a las 23:59 de Lima, y cómo se escribe su día y su mes. */
    private static final LocalDate VIERNES = LocalDate.now(FechaParaElCandidato.LIMA)
            .with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
    private static final Instant CIERRE = VIERNES.atTime(LocalTime.of(23, 59))
            .atZone(FechaParaElCandidato.LIMA).toInstant();
    private static final String DICHO = "vie %02d/%02d a las 23:59"
            .formatted(VIERNES.getDayOfMonth(), VIERNES.getMonthValue());

    @Test
    @DisplayName("cronometrada con fecha: los minutos desde que la empiece y hasta cuándo")
    void cronometradaConFecha() {
        assertThat(PlazoDeLaPrueba.dicho("CRONOMETRADA", 90, null, CIERRE))
                .isEqualTo("90 minutos desde que la empieces, hasta el " + DICHO);
    }

    @Test
    @DisplayName("sin cronómetro con fecha: solo hasta cuándo")
    void sinCronometroConFecha() {
        assertThat(PlazoDeLaPrueba.dicho(null, null, null, CIERRE)).isEqualTo("hasta el " + DICHO);
    }

    @Test
    @DisplayName("cronometrada sin fecha (de antes del editor nuevo): solo los minutos")
    void cronometradaSinFecha() {
        assertThat(PlazoDeLaPrueba.dicho("CRONOMETRADA", 90, null, null))
                .isEqualTo("90 minutos desde que la empieces");
    }

    @Test
    @DisplayName("plazo abierto con días y sin fecha: los días desde que la empiece")
    void plazoAbiertoSinFecha() {
        assertThat(PlazoDeLaPrueba.dicho("PLAZO_ABIERTO", null, 7, null))
                .isEqualTo("7 días desde que la empieces");
    }

    @Test
    @DisplayName("nunca vacío: sin minutos, sin días y sin fecha dice «sin fecha límite»")
    void nuncaVacio() {
        assertThat(PlazoDeLaPrueba.dicho(null, null, null, null)).isEqualTo("sin fecha límite");
        // Unos minutos en una «Sin cronómetro» no se dicen: mentirían
        assertThat(PlazoDeLaPrueba.dicho(null, 90, null, null)).isEqualTo("sin fecha límite");
        assertThat(PlazoDeLaPrueba.dicho("CRONOMETRADA", 0, null, null)).isEqualTo("sin fecha límite");
    }

    @Test
    @DisplayName("en singular cuando es uno")
    void enSingular() {
        assertThat(PlazoDeLaPrueba.dicho("CRONOMETRADA", 1, null, null))
                .isEqualTo("1 minuto desde que la empieces");
        assertThat(PlazoDeLaPrueba.dicho("PLAZO_ABIERTO", null, 1, null))
                .isEqualTo("1 día desde que la empieces");
    }

    @Test
    @DisplayName("la fecha va en hora de Lima: las 04:59 del sábado en UTC son el viernes a las 23:59")
    void laFechaVaEnLima() {
        // Comparar en UTC le quitaría al candidato el último día entero de su plazo.
        assertThat(CIERRE.atZone(java.time.ZoneOffset.UTC).getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
        assertThat(FechaParaElCandidato.dicha(CIERRE)).isEqualTo(DICHO);
    }

    @Test
    @DisplayName("cada día de la semana con su abreviatura, sin depender del idioma del servidor")
    void losSieteDias() {
        LocalDate lunes = VIERNES.with(TemporalAdjusters.previous(DayOfWeek.MONDAY));
        String[] esperados = {"lun", "mar", "mié", "jue", "vie", "sáb", "dom"};
        for (int i = 0; i < 7; i++) {
            Instant mediodia = lunes.plusDays(i).atTime(12, 0).atZone(FechaParaElCandidato.LIMA).toInstant();
            assertThat(FechaParaElCandidato.dicha(mediodia)).startsWith(esperados[i] + " ");
        }
    }
}
