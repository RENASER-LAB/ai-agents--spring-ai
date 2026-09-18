package com.renaser.ai.ai_engine.prueba.service;

import com.renaser.ai.ai_engine.prueba.entity.IntentoPrueba;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * En qué punto está una prueba del puesto, que es lo que decide el texto de la celda.
 *
 * <p>Lo que compila perfectamente estando mal:
 *
 * <ul>
 *   <li><b>Tratar el cero como ausencia.</b> Un cero es un juicio hecho, y quien lo sacó
 *       rindió: leerlo como «no hay nota» lo convertiría en alguien que no la terminó.</li>
 *   <li><b>Dar por entregada una prueba que cerró el sistema.</b> Al vencer el plazo se
 *       entrega lo que haya, y eso no es el candidato diciendo «ya está»: mientras no tenga
 *       nota, sigue siendo una prueba sin terminar.</li>
 *   <li><b>Llamar «pendiente de calificación» a quien no ha llegado a la etapa.</b> Sin
 *       intento no hay nada entregado, y prometer trabajo del equipo donde no lo hay llena la
 *       bandeja de gente a la que no hay que mirar.</li>
 * </ul>
 *
 * <p>Las fechas son relativas al momento de la prueba: una fecha escrita a mano caduca y el
 * día que caduque el fallo no dirá nada de esta regla.
 */
@DisplayName("El estado de la prueba del puesto")
class EstadoPruebaDelPuestoTest {

    private static final Long POSTULACION = 44L;

    @Test
    @DisplayName("con nota de etapa está calificada, aunque la entrega la hiciera el sistema")
    void conNotaMandaLaNota() {
        assertThat(EstadoPruebaDelPuesto.de(new BigDecimal("73"), entregada(true)))
                .isEqualTo(EstadoPruebaDelPuesto.CALIFICADA);
        assertThat(EstadoPruebaDelPuesto.de(new BigDecimal("73"), entregada(false)))
                .isEqualTo(EstadoPruebaDelPuesto.CALIFICADA);
    }

    /** Un cero es una nota. Es la frontera que convierte a quien rindió mal en quien no rindió. */
    @Test
    @DisplayName("un cero es nota, no un hueco")
    void elCeroEsNota() {
        assertThat(EstadoPruebaDelPuesto.de(BigDecimal.ZERO, entregada(false)))
                .isEqualTo(EstadoPruebaDelPuesto.CALIFICADA);
    }

    @Test
    @DisplayName("sin empezar todavía, la prueba está incompleta")
    void elIntentoPendienteEstaIncompleto() {
        assertThat(EstadoPruebaDelPuesto.de(null, pendiente()))
                .isEqualTo(EstadoPruebaDelPuesto.INCOMPLETA);
    }

    @Test
    @DisplayName("empezada y sin entregar, también está incompleta")
    void elIntentoEnCursoEstaIncompleto() {
        assertThat(EstadoPruebaDelPuesto.de(null, enCurso()))
                .isEqualTo(EstadoPruebaDelPuesto.INCOMPLETA);
    }

    @Test
    @DisplayName("la que cerró el sistema al vencer el plazo sigue incompleta mientras no haya nota")
    void laEntregaAutomaticaSinNotaEstaIncompleta() {
        assertThat(EstadoPruebaDelPuesto.de(null, entregada(true)))
                .isEqualTo(EstadoPruebaDelPuesto.INCOMPLETA);
    }

    @Test
    @DisplayName("la que entregó una persona y no tiene nota espera al equipo")
    void laEntregaManualSinNotaEsperaAlEquipo() {
        assertThat(EstadoPruebaDelPuesto.de(null, entregada(false)))
                .isEqualTo(EstadoPruebaDelPuesto.PENDIENTE_CALIFICACION);
    }

    @Test
    @DisplayName("sin intento no aplica, y nunca es «pendiente de calificación»")
    void sinIntentoNoAplica() {
        assertThat(EstadoPruebaDelPuesto.de(null, null))
                .isEqualTo(EstadoPruebaDelPuesto.NO_APLICA);
    }

    // ---- Los intentos, con fechas relativas ----

    /** Ni abierta: el candidato tiene la prueba asignada y no la ha empezado. */
    private IntentoPrueba pendiente() {
        return IntentoPrueba.builder().id(1L).postulacionId(POSTULACION)
                .venceEn(Instant.now().plus(Duration.ofDays(2))).build();
    }

    /** Abierta y dentro del plazo: el reloj corre y no ha entregado. */
    private IntentoPrueba enCurso() {
        return IntentoPrueba.builder().id(2L).postulacionId(POSTULACION)
                .iniciadoEn(Instant.now().minus(Duration.ofMinutes(20)))
                .venceEn(Instant.now().plus(Duration.ofMinutes(40))).build();
    }

    /** Entregada hace un rato, por el candidato o por el sistema al vencer el plazo. */
    private IntentoPrueba entregada(boolean porElSistema) {
        return IntentoPrueba.builder().id(3L).postulacionId(POSTULACION)
                .iniciadoEn(Instant.now().minus(Duration.ofHours(2)))
                .venceEn(Instant.now().minus(Duration.ofHours(1)))
                .entregadoEn(Instant.now().minus(Duration.ofHours(1)))
                .esEntregaAutomatica(porElSistema).build();
    }
}
