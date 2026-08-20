package com.renaser.ai.ai_engine.perfilintegral.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las siete fórmulas del banco v3, contra los ejemplos de la sección 0.2 del documento.
 *
 * <p>Se prueban sueltas porque son las que deciden la nota de una persona: si una se
 * equivoca, nadie ve un error, se ve una nota distinta.
 */
@DisplayName("Las fórmulas de puntuación del banco v3")
class FormulasBancoV3Test {

    private static void esIgualA(BigDecimal real, String esperado) {
        assertThat(real).isEqualByComparingTo(new BigDecimal(esperado));
    }

    @Nested
    @DisplayName("EF-4 · elección forzada")
    class Ef4 {

        @Test
        @DisplayName("Marcar la mejor como MÁS y la peor como MENOS da el máximo")
        void extremos() {
            esIgualA(FormulasBancoV3.ef4(2, -2), "3");    // bruto +4
        }

        @Test
        @DisplayName("Los cuatro tramos del documento, en sus bordes")
        void tramos() {
            esIgualA(FormulasBancoV3.ef4(-2, 2), "0");    // bruto −4
            esIgualA(FormulasBancoV3.ef4(0, 2), "0");     // bruto −2, último del tramo
            esIgualA(FormulasBancoV3.ef4(1, 2), "1");     // bruto −1, primero del siguiente
            esIgualA(FormulasBancoV3.ef4(0, 0), "1");     // bruto 0
            esIgualA(FormulasBancoV3.ef4(1, 0), "2");     // bruto +1
            esIgualA(FormulasBancoV3.ef4(2, 0), "2");     // bruto +2
            esIgualA(FormulasBancoV3.ef4(1, -2), "3");    // bruto +3
        }
    }

    @Nested
    @DisplayName("SJT-R · situacional con calificación")
    class SjtR {

        private final Map<String, Integer> claves = Map.of("a", 5, "b", 2, "c", 5, "d", 1, "e", 3);

        @Test
        @DisplayName("Clavar las cinco calificaciones da el máximo")
        void exacto() {
            esIgualA(FormulasBancoV3.sjtR(claves, claves), "3");
        }

        @Test
        @DisplayName("Fallar por uno en cada opción vale la mitad")
        void porUno() {
            Map<String, Integer> suyas = Map.of("a", 4, "b", 3, "c", 4, "d", 2, "e", 4);
            esIgualA(FormulasBancoV3.sjtR(suyas, claves), "1.50");
        }

        @Test
        @DisplayName("Fallar por dos o más no suma nada")
        void lejos() {
            Map<String, Integer> suyas = Map.of("a", 1, "b", 5, "c", 1, "d", 5, "e", 1);
            esIgualA(FormulasBancoV3.sjtR(suyas, claves), "0");
        }

        @Test
        @DisplayName("Lo que no contestó no resta, simplemente no suma")
        void sinResponder() {
            esIgualA(FormulasBancoV3.sjtR(Map.of("a", 5), claves), "0.60");
        }
    }

    @Nested
    @DisplayName("SEC · ordenamiento")
    class Sec {

        private final List<Integer> correcto = List.of(3, 1, 2, 4, 5);

        @Test
        @DisplayName("El orden exacto da el máximo")
        void exacto() {
            esIgualA(FormulasBancoV3.sec(correcto, correcto), "3");
        }

        @Test
        @DisplayName("El orden justo al revés no acierta ni un par")
        void alReves() {
            esIgualA(FormulasBancoV3.sec(List.of(5, 4, 2, 1, 3), correcto), "0");
        }

        @Test
        @DisplayName("Cambiar dos pasos de sitio cuesta un par de diez, no el ítem entero")
        void unaPareja() {
            esIgualA(FormulasBancoV3.sec(List.of(1, 3, 2, 4, 5), correcto), "2.70");
        }
    }

    @Nested
    @DisplayName("INV · inventario con distractores")
    class Inv {

        @Test
        @DisplayName("Marcar todas las reales y ninguna inventada da el máximo")
        void limpio() {
            esIgualA(FormulasBancoV3.inv(10, 10, 0), "3");
        }

        @Test
        @DisplayName("Marcar la mitad de las reales da la mitad")
        void mitad() {
            esIgualA(FormulasBancoV3.inv(5, 10, 0), "1.50");
        }

        @Test
        @DisplayName("Inventarse dos borra el ítem entero, aunque acierte todas las reales")
        void inflado() {
            esIgualA(FormulasBancoV3.inv(10, 10, 2), "0");
        }

        @Test
        @DisplayName("La nota nunca baja de cero por muchos falsos que marque")
        void nuncaNegativo() {
            esIgualA(FormulasBancoV3.inv(0, 10, 5), "0");
        }

        @Test
        @DisplayName("Dos elementos inventados levantan bandera; uno todavía no")
        void bandera() {
            assertThat(FormulasBancoV3.banderaDeInflacion(1)).isFalse();
            assertThat(FormulasBancoV3.banderaDeInflacion(2)).isTrue();
        }
    }

    @Nested
    @DisplayName("DE · detección de error")
    class De {

        @Test
        @DisplayName("Las cuatro ciertas y ninguna falsa da el máximo")
        void perfecto() {
            esIgualA(FormulasBancoV3.de(4, 0), "3");
        }

        @Test
        @DisplayName("Marcarlo todo no sirve: las falsas descuentan las ciertas")
        void marcarTodo() {
            esIgualA(FormulasBancoV3.de(4, 4), "0");
        }

        @Test
        @DisplayName("Dos ciertas sin fallos valen la mitad")
        void mitad() {
            esIgualA(FormulasBancoV3.de(2, 0), "1.50");
        }
    }

    @Nested
    @DisplayName("CD · caso descompuesto")
    class Cd {

        @Test
        @DisplayName("Todos los campos válidos dan el máximo")
        void completo() {
            esIgualA(FormulasBancoV3.cd(7, 7), "3");
        }

        @Test
        @DisplayName("Un caso a medias puntúa la parte que sí es válida")
        void aMedias() {
            esIgualA(FormulasBancoV3.cd(4, 7), "1.71");
        }

        @Test
        @DisplayName("Un caso sin ningún campo válido no puntúa, y no revienta")
        void vacio() {
            esIgualA(FormulasBancoV3.cd(0, 7), "0");
            esIgualA(FormulasBancoV3.cd(0, 0), "0");
        }
    }

    @Nested
    @DisplayName("El peso del ítem")
    class Peso {

        @Test
        @DisplayName("Peso 1 llega a 3 puntos, peso 2 llega a 6 y peso 0 no suma")
        void losTresPesos() {
            BigDecimal maximo = new BigDecimal("3");
            esIgualA(FormulasBancoV3.conPeso(maximo, 1), "3");
            esIgualA(FormulasBancoV3.conPeso(maximo, 2), "6");
            esIgualA(FormulasBancoV3.conPeso(maximo, 0), "0");
        }

        @Test
        @DisplayName("Los 85 ítems del banco directivo suman los 288 que declara el documento")
        void elMaximoDelBancoDirectivo() {
            // 81 puntuables: 66 de peso 1 y 15 de peso 2 (los marcados con estrella).
            // Es la comprobación que ata las fórmulas al documento: si el techo no fuera 3
            // por ítem de peso 1, este número no saldría.
            BigDecimal tope = new BigDecimal("3");
            BigDecimal total = FormulasBancoV3.conPeso(tope, 1).multiply(new BigDecimal("66"))
                    .add(FormulasBancoV3.conPeso(tope, 2).multiply(new BigDecimal("15")));
            esIgualA(total, "288");
        }
    }
}
