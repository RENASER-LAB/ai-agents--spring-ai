package com.renaser.ai.ai_engine.vacante.service;

import com.renaser.ai.ai_engine.vacante.entity.Vacante;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Lo que la vacante dice sobre el dinero")
class RemuneracionTest {

    private static BigDecimal soles(String cuanto) {
        return new BigDecimal(cuanto);
    }

    @Nested
    @DisplayName("Validar lo que declara la empresa")
    class Validar {

        @Test
        @DisplayName("una vacante OCULTA no necesita montos, y los que lleguen se ignoran")
        void ocultaNoNecesitaNada() {
            assertThat(Remuneracion.validar("OCULTA", null, null, null)).isNull();
            // Quien apaga la remuneración desde una pantalla que tenía las cifras escritas no
            // debe recibir un error por unos números que ya no significan nada.
            assertThat(Remuneracion.validar("OCULTA", soles("3000"), soles("4000"), "PEN"))
                    .isNull();
        }

        @Test
        @DisplayName("una FIJA necesita su monto y no admite máximo")
        void fijaLlevaUnSoloMonto() {
            assertThat(Remuneracion.validar("FIJA", soles("3500"), null, "PEN"))
                    .isEqualTo("PEN");

            assertThatThrownBy(() -> Remuneracion.validar("FIJA", null, null, "PEN"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Falta el monto");

            assertThatThrownBy(() ->
                    Remuneracion.validar("FIJA", soles("3500"), soles("4000"), "PEN"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("un solo monto");
        }

        @Test
        @DisplayName("un RANGO al revés no se guarda")
        void elRangoVaEnOrden() {
            assertThat(Remuneracion.validar("RANGO", soles("3000"), soles("4000"), "PEN"))
                    .isEqualTo("PEN");
            // Iguales sí: una banda de un solo punto es rara pero no es un error.
            assertThat(Remuneracion.validar("RANGO", soles("3000"), soles("3000"), "PEN"))
                    .isEqualTo("PEN");

            assertThatThrownBy(() ->
                    Remuneracion.validar("RANGO", soles("4000"), soles("3000"), "PEN"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("máximo del rango no puede ser menor");
        }

        @Test
        @DisplayName("la moneda se normaliza, y fuera de PEN/USD no entra")
        void soloDosMonedas() {
            assertThat(Remuneracion.validar("FIJA", soles("3500"), null, " pen "))
                    .isEqualTo("PEN");
            assertThat(Remuneracion.validar("FIJA", soles("1200"), null, "usd"))
                    .isEqualTo("USD");

            assertThatThrownBy(() -> Remuneracion.validar("FIJA", soles("3500"), null, "EUR"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("PEN o USD");
            assertThatThrownBy(() -> Remuneracion.validar("FIJA", soles("3500"), null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("cero, negativo y la cifra absurda se paran aquí")
        void losMontosTienenSueloYTecho() {
            assertThatThrownBy(() -> Remuneracion.validar("FIJA", soles("0"), null, "PEN"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("mayores que cero");
            assertThatThrownBy(() -> Remuneracion.validar("FIJA", soles("-100"), null, "PEN"))
                    .isInstanceOf(IllegalArgumentException.class);
            // El dedo de más: 35 000 000 donde quería 3 500.
            assertThatThrownBy(() ->
                    Remuneracion.validar("FIJA", soles("35000000"), null, "PEN"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("error de tecleo");
        }

        /**
         * El suelo y la regla del entero, que llegaron juntos y por el mismo motivo.
         *
         * <p>El formulario del portal leía «3,500» como <b>3.5</b> —`Number('3,500')` en
         * JavaScript— y las validaciones de aquí lo dejaban pasar: era mayor que cero y menor
         * que el techo. El resultado era una vacante publicada prometiendo tres soles con
         * cincuenta, y cuarenta correos diciéndolo. El formulario ya está arreglado, pero la
         * API no es solo el formulario: esto es lo que la cierra por debajo.
         */
        @Test
        @DisplayName("un sueldo con céntimos no es un sueldo: se rechaza en los dos lados")
        void losSueldosVanEnCifrasEnteras() {
            assertThatThrownBy(() -> Remuneracion.validar("FIJA", soles("3.50"), null, "PEN"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cifras enteras");
            assertThatThrownBy(() ->
                    Remuneracion.validar("RANGO", soles("3000"), soles("4000.75"), "PEN"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cifras enteras");
            assertThatThrownBy(() -> Remuneracion.validarPretension(soles("3.50"), "PEN"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cifras enteras");

            // Pero «3500.00» sí: son ceros, no céntimos — y es exactamente como vuelve una
            // cifra guardada en una columna `numeric(12,2)`. Rechazarla haría que abrir la
            // pantalla y pulsar guardar sin tocar nada fuera un error.
            assertThat(Remuneracion.validar("FIJA", soles("3500.00"), null, "PEN"))
                    .isEqualTo("PEN");
            assertThat(Remuneracion.validarPretension(soles("3800.00"), "PEN"))
                    .isEqualTo("PEN");
        }

        @Test
        @DisplayName("y hay suelo: S/ 50 al mes no es una oferta baja, es un error")
        void haySuelo() {
            assertThatThrownBy(() -> Remuneracion.validar("FIJA", soles("50"), null, "PEN"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("demasiado bajo");
            assertThatThrownBy(() -> Remuneracion.validarPretension(soles("50"), "PEN"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("demasiado baja");
            // Cien justo entra: el suelo para el error de magnitud, no la oferta modesta, y
            // la cifra también viaja en dólares.
            assertThat(Remuneracion.validar("FIJA", soles("100"), null, "USD"))
                    .isEqualTo("USD");
        }

        @Test
        @DisplayName("un tipo inventado no pasa")
        void soloTresTipos() {
            assertThatThrownBy(() ->
                    Remuneracion.validar("A_CONVENIR", null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("OCULTA, FIJA o RANGO");
        }
    }

    @Nested
    @DisplayName("El trato: quién obliga a quién")
    class ElTrato {

        @Test
        @DisplayName("enseñar el sueldo es lo que exige declarar el propio")
        void ensenarObliga() {
            assertThat(Remuneracion.laEnsena(vacante("OCULTA"))).isFalse();
            assertThat(Remuneracion.laEnsena(vacante("FIJA"))).isTrue();
            assertThat(Remuneracion.laEnsena(vacante("RANGO"))).isTrue();
        }

        @Test
        @DisplayName("una vacante anterior a la V54 —con el tipo vacío— cuenta como OCULTA")
        void laFilaViejaEsOculta() {
            // Es el estado real de todas las vacantes que existían al desplegar: la columna
            // tiene DEFAULT, pero una entidad construida a mano puede traerlo en null y no
            // puede acabar exigiéndole una pretensión a nadie por un descuido.
            assertThat(Remuneracion.laEnsena(vacante(null))).isFalse();
            assertThat(Remuneracion.tipoDe(vacante(null))).isEqualTo("OCULTA");
            assertThat(Remuneracion.tipoDe(vacante("  "))).isEqualTo("OCULTA");
        }

        private Vacante vacante(String tipo) {
            return Vacante.builder().remuneracionTipo(tipo).build();
        }
    }

    @Nested
    @DisplayName("Escribir el dinero en una frase")
    class Escribir {

        @Test
        @DisplayName("una OCULTA dice «sin publicar», no un guion")
        void laOcultaSeNombra() {
            // El correo del cambio puede tener la OCULTA en cualquiera de los dos lados, y un
            // guion deja al candidato sin saber si es que no le dijeron o es que no hay.
            assertThat(Remuneracion.escribir("OCULTA", null, null, null))
                    .isEqualTo("sin publicar");
            assertThat(Remuneracion.escribir(null, null, null, null))
                    .isEqualTo("sin publicar");
        }

        @Test
        @DisplayName("los miles se separan y los céntimos de cero no se escriben")
        void seLeeDeUnVistazo() {
            assertThat(Remuneracion.escribir("FIJA", soles("3500"), null, "PEN"))
                    .isEqualTo("S/ 3 500");
            assertThat(Remuneracion.escribir("FIJA", soles("3500.00"), null, "PEN"))
                    .isEqualTo("S/ 3 500");
            assertThat(Remuneracion.escribir("RANGO", soles("3000"), soles("4200"), "PEN"))
                    .isEqualTo("S/ 3 000 a 4 200");
            assertThat(Remuneracion.escribir("FIJA", soles("1200"), null, "USD"))
                    .isEqualTo("US$ 1 200");
        }

        @Test
        @DisplayName("los céntimos que existen de verdad sí salen")
        void losCentimosRealesSeRespetan() {
            assertThat(Remuneracion.escribir("FIJA", soles("3500.50"), null, "PEN"))
                    .isEqualTo("S/ 3 500.50");
        }

        @Test
        @DisplayName("la pretensión sin declarar se nombra, no se deja en blanco")
        void laPretensionVaciaSeNombra() {
            assertThat(Remuneracion.escribirPretension(null, "PEN")).isEqualTo("sin declarar");
            assertThat(Remuneracion.escribirPretension(soles("3800"), "PEN"))
                    .isEqualTo("S/ 3 800");
        }
    }

    @Nested
    @DisplayName("Lo que se le sugiere al candidato desde su perfil")
    class Sugerir {

        @Test
        @DisplayName("con banda completa se propone el centro, no el borde bajo")
        void elCentroDeLaBanda() {
            // Prellenar con el mínimo le regalaría a la empresa el borde bajo de su propia
            // expectativa cada vez que alguien pulsa enviar sin mirar.
            assertThat(Remuneracion.sugerirDesdeElPerfil(soles("3000"), soles("4000")))
                    .isEqualByComparingTo("3500");
            // Sin decimales: es una sugerencia, no una liquidación.
            assertThat(Remuneracion.sugerirDesdeElPerfil(soles("3000"), soles("4001")))
                    .isEqualByComparingTo("3501");
        }

        @Test
        @DisplayName("con media banda se propone lo que haya; sin nada, nada")
        void loQueHaya() {
            assertThat(Remuneracion.sugerirDesdeElPerfil(soles("3000"), null))
                    .isEqualByComparingTo("3000");
            assertThat(Remuneracion.sugerirDesdeElPerfil(null, soles("4000")))
                    .isEqualByComparingTo("4000");
            // El caso de casi todo el mundo: el campo sale vacío y lo escribe él.
            assertThat(Remuneracion.sugerirDesdeElPerfil(null, null)).isNull();
        }
    }
}
