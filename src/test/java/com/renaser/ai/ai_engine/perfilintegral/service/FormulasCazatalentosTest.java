package com.renaser.ai.ai_engine.perfilintegral.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Las fórmulas del banco CAZATALENTOS, contra los ejemplos del documento de la clienta.
 *
 * <p>Los cuatro candidatos son los de la parte 1.4 (pregunta R15, bajo rendimiento). Son el
 * contrato: si un cambio en las fórmulas mueve uno de estos puntajes, no es una mejora, es
 * otro instrumento.
 */
@DisplayName("Las fórmulas de puntuación del banco CAZATALENTOS")
class FormulasCazatalentosTest {

    private static void esIgualA(BigDecimal real, String esperado) {
        assertThat(real).isEqualByComparingTo(new BigDecimal(esperado));
    }

    @Nested
    @DisplayName("El puntaje del ítem: contar criterios, con sus dos compuertas")
    class Puntaje {

        @Test
        @DisplayName("Candidato A: pura filosofía, sin episodio = 0")
        void candidatoA() {
            // «Siempre trabajo el bajo rendimiento con retroalimentación continua…»
            assertThat(FormulasCazatalentos.puntaje(false, false, false, false, false)).isZero();
        }

        @Test
        @DisplayName("Candidato B: episodio en «nosotros», nada concreto = 1")
        void candidatoB() {
            // «Tuvimos un caso… trabajamos un plan de mejora y logramos que se recuperara.»
            assertThat(FormulasCazatalentos.puntaje(false, true, false, false, false)).isEqualTo(1);
        }

        @Test
        @DisplayName("Candidato C: episodio suyo con dato duro, sin la parte incómoda = 3")
        void candidatoC() {
            // «Tenía 14 personas… plan por escrito con corte al 30 y seguimiento los viernes.»
            assertThat(FormulasCazatalentos.puntaje(false, true, true, true, false)).isEqualTo(3);
        }

        @Test
        @DisplayName("Candidato D: dice lo mismo que C sobre lo que hizo, más lo que le costó = 4")
        void candidatoD() {
            // «…Me costó porque era amigo del dueño… perdí relación con el dueño un tiempo.»
            assertThat(FormulasCazatalentos.puntaje(false, true, true, true, true)).isEqualTo(4);
        }

        @Test
        @DisplayName("La señal de 0 corta el cálculo aunque haya criterios presentes")
        void senalDeCero() {
            // R18: acepta dejarlo pasar. Da igual lo bien contado que esté.
            assertThat(FormulasCazatalentos.puntaje(true, true, true, true, true)).isZero();
        }

        @Test
        @DisplayName("Sin episodio no se cuenta nada: C2, C3 o C4 sueltos no suman")
        void sinEpisodioNoHayNada() {
            // «Cómo se califica»: si C1 está ausente, el puntaje máximo es 0.
            assertThat(FormulasCazatalentos.puntaje(false, false, true, true, true)).isZero();
        }

        @Test
        @DisplayName("Episodio ajeno con dato duro vale 2: el conteo es literal")
        void conteoLiteral() {
            // Paso 2 del documento: «el puntaje es el número de criterios presentes».
            assertThat(FormulasCazatalentos.puntaje(false, true, false, true, false)).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("La regla dura de R11: sin ninguna cifra, el máximo es 2")
    class ReglaDura {

        @Test
        @DisplayName("Sin C3, el tope recorta un 3 a 2")
        void recorta() {
            // C1 ✓ C2 ✓ C4 ✓ sin cifra: contaría 3, la regla lo deja en 2.
            assertThat(FormulasCazatalentos.puntaje(false, true, true, false, true, 2)).isEqualTo(2);
        }

        @Test
        @DisplayName("Con C3 presente el tope no actúa")
        void conDatoNoActua() {
            assertThat(FormulasCazatalentos.puntaje(false, true, true, true, true, 2)).isEqualTo(4);
        }

        @Test
        @DisplayName("Una pregunta sin tope declarado cuenta normal")
        void sinTope() {
            assertThat(FormulasCazatalentos.puntaje(false, true, true, false, true, null)).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("El puntaje de pilar: obtenidos sobre 4 × la suma de pesos")
    class Pilar {

        @Test
        @DisplayName("Todo perfecto da 100, también con un ítem de peso 2")
        void maximo() {
            // Pilar 5 de DIR: R11 (peso 2), R12 y R13 (peso 1). Máximo = 4 × 4 = 16.
            int obtenidos = FormulasCazatalentos.conPeso(4, 2)
                    + FormulasCazatalentos.conPeso(4, 1)
                    + FormulasCazatalentos.conPeso(4, 1);
            esIgualA(FormulasCazatalentos.puntajePilar(obtenidos, 4), "100");
        }

        @Test
        @DisplayName("El peso 2 pesa doble arriba y doble abajo")
        void pesoDoble() {
            // R11 con 4 y las otras dos con 0: 8 de 16 = 50, no 8 de 12.
            esIgualA(FormulasCazatalentos.puntajePilar(FormulasCazatalentos.conPeso(4, 2), 4), "50");
        }

        @Test
        @DisplayName("Un pilar de una sola pregunta (Integridad) se puntúa igual")
        void unaSola() {
            esIgualA(FormulasCazatalentos.puntajePilar(3, 1), "75");
        }

        @Test
        @DisplayName("Sin pesos que sumar, el pilar vale 0 en vez de dividir por cero")
        void sinPreguntas() {
            esIgualA(FormulasCazatalentos.puntajePilar(0, 0), "0");
        }
    }

    @Nested
    @DisplayName("El índice: la suma ponderada de los pilares")
    class Indice {

        @Test
        @DisplayName("Todos los pilares a 100 dan un índice de 100")
        void maximo() {
            Map<String, BigDecimal> cien = Map.of(
                    "PIL_INICIATIVA", new BigDecimal("100"),
                    "PIL_RESOLUCION", new BigDecimal("100"));
            Map<String, BigDecimal> pesos = Map.of(
                    "PIL_INICIATIVA", new BigDecimal("40"),
                    "PIL_RESOLUCION", new BigDecimal("60"));
            esIgualA(FormulasCazatalentos.indice(cien, pesos), "100");
        }

        @Test
        @DisplayName("Un caso DIR MEDIA/GRANDE calculado a mano")
        void casoAMano() {
            // Pesos del documento (parte 8.2), sin Integridad, que es eliminatoria.
            Map<String, BigDecimal> pesos = Map.of(
                    "PIL_INICIATIVA", new BigDecimal("15"),
                    "PIL_RESOLUCION", new BigDecimal("22"),
                    "PIL_EXCELENCIA", new BigDecimal("15"),
                    "PIL_SERVICIO", new BigDecimal("10"),
                    "PIL_RESPONSABILIDAD", new BigDecimal("18"),
                    "PIL_DIRECCION", new BigDecimal("20"));
            Map<String, BigDecimal> puntajes = Map.of(
                    "PIL_INICIATIVA", new BigDecimal("50"),
                    "PIL_RESOLUCION", new BigDecimal("75"),
                    "PIL_EXCELENCIA", new BigDecimal("62.50"),
                    "PIL_SERVICIO", new BigDecimal("25"),
                    "PIL_RESPONSABILIDAD", new BigDecimal("81.25"),
                    "PIL_DIRECCION", new BigDecimal("60"));
            // 7.5 + 16.5 + 9.375 + 2.5 + 14.625 + 12 = 62.5 → pasa el corte de 60.
            esIgualA(FormulasCazatalentos.indice(puntajes, pesos), "62.50");
        }

        @Test
        @DisplayName("Un pilar con peso y sin puntaje es un error de datos, no un cero")
        void pilarSinPuntaje() {
            Map<String, BigDecimal> pesos = Map.of("PIL_DIRECCION", new BigDecimal("20"));
            assertThatThrownBy(() -> FormulasCazatalentos.indice(Map.of(), pesos))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("PIL_DIRECCION");
        }
    }
}
