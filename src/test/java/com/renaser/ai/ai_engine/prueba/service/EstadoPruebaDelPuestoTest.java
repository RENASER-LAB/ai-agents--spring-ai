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
 *   <li><b>Llamar «incompleta» a quien la está haciendo</b> (V89). Sin empezar, en curso y
 *       «no la completó» son tres cosas: solo la última es que se le acabó el tiempo.</li>
 *   <li><b>Confundir la entrega automática con huecos y la completa.</b> La del editor solo se
 *       entrega sola con todo respondido —espera nota—; con huecos se cierra como «no
 *       completada».</li>
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
    @DisplayName("sin abrir y con plazo vigente, está sin empezar (V89)")
    void elIntentoPendienteEstaSinEmpezar() {
        assertThat(EstadoPruebaDelPuesto.de(null, pendiente()))
                .isEqualTo(EstadoPruebaDelPuesto.SIN_EMPEZAR);
    }

    @Test
    @DisplayName("sin abrir y sin fecha, también está sin empezar: no hay plazo que se le pase")
    void sinFechaEstaSinEmpezar() {
        IntentoPrueba sinFecha = IntentoPrueba.builder().id(5L).postulacionId(POSTULACION).build();
        assertThat(EstadoPruebaDelPuesto.de(null, sinFecha))
                .isEqualTo(EstadoPruebaDelPuesto.SIN_EMPEZAR);
    }

    @Test
    @DisplayName("empezada y con el reloj corriendo, está en curso: nunca «incompleta» (V89)")
    void elIntentoEnCursoEstaEnCurso() {
        assertThat(EstadoPruebaDelPuesto.de(null, enCurso()))
                .isEqualTo(EstadoPruebaDelPuesto.EN_CURSO);
    }

    @Test
    @DisplayName("se le pasó la fecha sin abrirla: no la completó (V89)")
    void vencidaSinAbrirNoLaCompleto() {
        IntentoPrueba vencida = IntentoPrueba.builder().id(6L).postulacionId(POSTULACION)
                .venceEn(Instant.now().minus(Duration.ofHours(3))).build();
        assertThat(EstadoPruebaDelPuesto.de(null, vencida))
                .isEqualTo(EstadoPruebaDelPuesto.NO_COMPLETADA);
    }

    @Test
    @DisplayName("la fecha justa cuenta como pasada: a esa hora ya no puede empezarla")
    void laFechaJustaYaPaso() {
        Instant ahora = Instant.now();
        IntentoPrueba justa = IntentoPrueba.builder().id(7L).postulacionId(POSTULACION)
                .venceEn(ahora).build();
        assertThat(EstadoPruebaDelPuesto.de(null, justa, ahora))
                .isEqualTo(EstadoPruebaDelPuesto.NO_COMPLETADA);
        assertThat(EstadoPruebaDelPuesto.de(null, justa, ahora.minusSeconds(1)))
                .isEqualTo(EstadoPruebaDelPuesto.SIN_EMPEZAR);
    }

    @Test
    @DisplayName("la del editor que venció con huecos no la completó (V67, V89)")
    void laNoCompletadaNoLaCompleto() {
        IntentoPrueba conHuecos = IntentoPrueba.builder().id(8L).postulacionId(POSTULACION)
                .versionBancoId(70L)
                .iniciadoEn(Instant.now().minus(Duration.ofHours(2)))
                .venceEn(Instant.now().minus(Duration.ofHours(1)))
                .entregadoEn(Instant.now().minus(Duration.ofHours(1)))
                .esEntregaAutomatica(true).noCompletada(true).build();
        assertThat(EstadoPruebaDelPuesto.de(null, conHuecos))
                .isEqualTo(EstadoPruebaDelPuesto.NO_COMPLETADA);
    }

    @Test
    @DisplayName("la que se entregó sola al vencer, con todo respondido, espera nota (V89)")
    void laEntregaAutomaticaCompletaEsperaNota() {
        assertThat(EstadoPruebaDelPuesto.de(null, entregada(true)))
                .isEqualTo(EstadoPruebaDelPuesto.PENDIENTE_CALIFICACION);
    }

    @Test
    @DisplayName("la que entregó una persona y no tiene nota espera al equipo")
    void laEntregaManualSinNotaEsperaAlEquipo() {
        assertThat(EstadoPruebaDelPuesto.de(null, entregada(false)))
                .isEqualTo(EstadoPruebaDelPuesto.PENDIENTE_CALIFICACION);
    }

    @Test
    @DisplayName("con nota manda la nota, aunque no la hubiera completado")
    void conNotaMandaAunqueNoLaCompletara() {
        IntentoPrueba conHuecos = IntentoPrueba.builder().id(9L).postulacionId(POSTULACION)
                .versionBancoId(70L).entregadoEn(Instant.now()).noCompletada(true).build();
        assertThat(EstadoPruebaDelPuesto.de(BigDecimal.TEN, conHuecos))
                .isEqualTo(EstadoPruebaDelPuesto.CALIFICADA);
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
