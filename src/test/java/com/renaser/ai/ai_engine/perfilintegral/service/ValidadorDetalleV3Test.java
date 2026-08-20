package com.renaser.ai.ai_engine.perfilintegral.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Las comprobaciones que la base de datos haría sola si el detalle fuera una tabla.
 *
 * <p>Como se guarda en jsonb, no hay clave foránea ni forma exigida: lo único que separa una
 * respuesta mal formada de una nota calculada sobre ella es este validador. Por eso se prueba
 * sobre todo lo que tiene que **rechazar**.
 */
@DisplayName("El validador del detalle de las respuestas v3")
class ValidadorDetalleV3Test {

    /** Las opciones que esa pregunta tiene de verdad. */
    private static final Set<Long> SUYAS = Set.of(10L, 11L, 12L, 13L, 14L);
    /** De otra pregunta: nada de esto debería colarse. */
    private static final long AJENA = 99L;

    private void rechaza(String tipo, Map<String, Object> detalle) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ValidadorDetalleV3.validar(tipo, SUYAS, detalle));
    }

    private void acepta(String tipo, Map<String, Object> detalle) {
        assertThatCode(() -> ValidadorDetalleV3.validar(tipo, SUYAS, detalle))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Sin detalle no se puede responder un formato que lo necesita")
    void sinDetalle() {
        rechaza("EF-4", null);
        rechaza("EF-4", Map.of());
    }

    @Test
    @DisplayName("Solo seis formatos se responden con detalle; los demás, no")
    void cualesLoNecesitan() {
        List.of("EF-4", "SJT-R", "SEC", "INV", "DE", "CD")
                .forEach(t -> org.assertj.core.api.Assertions
                        .assertThat(ValidadorDetalleV3.necesitaDetalle(t)).isTrue());
        List.of("V", "PC")
                .forEach(t -> org.assertj.core.api.Assertions
                        .assertThat(ValidadorDetalleV3.necesitaDetalle(t)).isFalse());
    }

    @Nested
    @DisplayName("EF-4")
    class Ef4 {

        @Test
        @DisplayName("Marcar una como más parecida y otra como menos es lo correcto")
        void bien() {
            acepta("EF-4", Map.of("mas", 10, "menos", 12));
        }

        @Test
        @DisplayName("La misma opción no puede ser la más y la menos parecida")
        void laMisma() {
            rechaza("EF-4", Map.of("mas", 10, "menos", 10));
        }

        @Test
        @DisplayName("No vale marcar una opción de otra pregunta")
        void ajena() {
            rechaza("EF-4", Map.of("mas", 10, "menos", AJENA));
        }

        @Test
        @DisplayName("Faltando una de las dos, la respuesta está incompleta")
        void incompleta() {
            rechaza("EF-4", Map.of("mas", 10));
        }
    }

    @Nested
    @DisplayName("SJT-R")
    class SjtR {

        @Test
        @DisplayName("Una calificación de 1 a 5 por opción")
        void bien() {
            acepta("SJT-R", Map.of("calificaciones", Map.of("10", 5, "11", 1)));
        }

        @Test
        @DisplayName("Fuera del 1 al 5 no es una calificación")
        void fueraDeRango() {
            rechaza("SJT-R", Map.of("calificaciones", Map.of("10", 6)));
            rechaza("SJT-R", Map.of("calificaciones", Map.of("10", 0)));
        }

        @Test
        @DisplayName("No se puede calificar una opción que no es de esta pregunta")
        void ajena() {
            rechaza("SJT-R", Map.of("calificaciones", Map.of(String.valueOf(AJENA), 3)));
        }

        @Test
        @DisplayName("Sin ninguna calificación no hay respuesta")
        void vacio() {
            rechaza("SJT-R", Map.of("calificaciones", Map.of()));
        }
    }

    @Nested
    @DisplayName("SEC")
    class Sec {

        @Test
        @DisplayName("Todos sus pasos, una vez cada uno")
        void bien() {
            acepta("SEC", Map.of("orden", List.of(12, 10, 11, 14, 13)));
        }

        @Test
        @DisplayName("Dejarse un paso invalida el orden entero")
        void faltaUno() {
            rechaza("SEC", Map.of("orden", List.of(12, 10, 11, 14)));
        }

        @Test
        @DisplayName("Repetir un paso tampoco es un orden")
        void repetido() {
            rechaza("SEC", Map.of("orden", List.of(10, 10, 11, 12, 13)));
        }

        @Test
        @DisplayName("Colar un paso de otra pregunta se rechaza")
        void ajeno() {
            rechaza("SEC", Map.of("orden", List.of(10, 11, 12, 13, AJENA)));
        }
    }

    @Nested
    @DisplayName("INV y DE")
    class Marcadas {

        @Test
        @DisplayName("Marcar algunas, o ninguna, es válido")
        void bien() {
            acepta("INV", Map.of("marcadas", List.of(10, 12)));
            acepta("DE", Map.of("marcadas", List.of()));
        }

        @Test
        @DisplayName("Marcar dos veces lo mismo inflaría la cuenta")
        void repetida() {
            rechaza("INV", Map.of("marcadas", List.of(10, 10)));
        }

        @Test
        @DisplayName("No se puede marcar un elemento de otra pregunta")
        void ajena() {
            rechaza("DE", Map.of("marcadas", List.of(10, AJENA)));
        }

        @Test
        @DisplayName("Si no es una lista, no se entiende")
        void noEsLista() {
            rechaza("INV", Map.of("marcadas", "10,12"));
        }
    }

    @Nested
    @DisplayName("CD")
    class Cd {

        @Test
        @DisplayName("Los campos del caso, con lo que puso en cada uno")
        void bien() {
            acepta("CD", Map.of("campos", Map.of("1", "reclamo de cliente", "2", "plan escrito")));
        }

        @Test
        @DisplayName("Un caso sin ningún campo no es una respuesta")
        void vacio() {
            rechaza("CD", Map.of("campos", Map.of()));
        }
    }

    @Test
    @DisplayName("Un detalle con la forma de otro formato se rechaza")
    void formaCruzada() {
        // Es justo lo que el jsonb deja pasar y una tabla no: mandar el detalle de un SEC
        // en un ítem EF-4.
        rechaza("EF-4", Map.of("orden", List.of(10, 11, 12, 13, 14)));
        rechaza("SEC", Map.of("mas", 10, "menos", 11));
    }
}
