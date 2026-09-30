package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.service.ReglasDePuntos.OpcionAValidar;
import com.renaser.ai.ai_engine.perfilintegral.service.ReglasDePuntos.PreguntaAValidar;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Las reglas de las preguntas propias (método PUNTOS)")
class ReglasDePuntosTest {

    private static BigDecimal n(String valor) {
        return new BigDecimal(valor);
    }

    private static OpcionAValidar op(String texto, String puntos) {
        return new OpcionAValidar(texto, n(puntos));
    }

    @Test
    @DisplayName("Opción múltiple de 10 con +6, +6 y −4: las tres dan 8, las dos buenas el tope, la mala el piso (AC-06)")
    void laMultipleTieneTopeYPiso() {
        assertThat(ReglasDePuntos.puntosDeCerrada("OPCION_MULTIPLE", 10, null,
                List.of(n("6"), n("6"), n("-4")))).isEqualByComparingTo("8");
        assertThat(ReglasDePuntos.puntosDeCerrada("OPCION_MULTIPLE", 10, null,
                List.of(n("6"), n("6")))).isEqualByComparingTo("10");
        assertThat(ReglasDePuntos.puntosDeCerrada("OPCION_MULTIPLE", 10, null,
                List.of(n("-4")))).isEqualByComparingTo("0");
        assertThat(ReglasDePuntos.puntosDeCerrada("OPCION_MULTIPLE", 10, null, List.of()))
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Opción única y escala: los puntos de lo elegido; sin responder, 0")
    void unicaYEscala() {
        assertThat(ReglasDePuntos.puntosDeCerrada("OPCION_UNICA", 4, n("4"), List.of()))
                .isEqualByComparingTo("4");
        assertThat(ReglasDePuntos.puntosDeCerrada("ESCALA", 9, n("7"), List.of()))
                .isEqualByComparingTo("7");
        assertThat(ReglasDePuntos.puntosDeCerrada("OPCION_UNICA", 4, null, List.of()))
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("La forma: lo vacío, lo inválido y los decimales se rechazan, todo junto")
    void laFormaLoDiceTodo() {
        List<String> faltas = ReglasDePuntos.formaDeLaPregunta("La pregunta",
                new PreguntaAValidar("OPCION_UNICA", " ", n("2.5"), null,
                        List.of(op("", "1.5"))));
        assertThat(faltas).anyMatch(f -> f.contains("enunciado"))
                .anyMatch(f -> f.contains("enteros"))
                .anyMatch(f -> f.contains("entre 2 y 10"))
                .anyMatch(f -> f.contains("no tiene texto"))
                .anyMatch(f -> f.contains("la opción 1 tienen que ser enteros"));
    }

    @Test
    @DisplayName("Puntos negativos en una pregunta, un tipo raro y una escala de dos niveles se rechazan")
    void negativosTiposYEscalaCorta() {
        assertThat(ReglasDePuntos.formaDeLaPregunta("P",
                new PreguntaAValidar("ABIERTA", "x", n("-1"), null, List.of())))
                .anyMatch(f -> f.contains("de 0 a 100"));
        assertThat(ReglasDePuntos.formaDeLaPregunta("P",
                new PreguntaAValidar("EF-4", "x", n("5"), null, List.of())))
                .anyMatch(f -> f.contains("el tipo"));
        assertThat(ReglasDePuntos.formaDeLaPregunta("P",
                new PreguntaAValidar("ESCALA", "x", n("5"), null,
                        List.of(op(null, "0"), op(null, "5")))))
                .anyMatch(f -> f.contains("entre 3 y 10 niveles"));
        // En la escala el rótulo es opcional: sin texto no es una falta
        assertThat(ReglasDePuntos.formaDeLaPregunta("P",
                new PreguntaAValidar("ESCALA", "x", n("5"), null,
                        List.of(op(null, "0"), op("", "2"), op("Mucho", "5"))))).isEmpty();
    }

    @Test
    @DisplayName("«Qué debe tener» de más de 1000 caracteres se rechaza")
    void queDebeTenerLargo() {
        assertThat(ReglasDePuntos.formaDeLaPregunta("P",
                new PreguntaAValidar("ABIERTA", "x", n("5"), "a".repeat(1001), List.of())))
                .anyMatch(f -> f.contains("1000"));
    }

    @Test
    @DisplayName("Única: ninguna negativa, ninguna por encima y al menos una da el máximo")
    void laPuntuacionDeLaUnica() {
        assertThat(ReglasDePuntos.puntuacionDeLaPregunta("P",
                new PreguntaAValidar("OPCION_UNICA", "x", n("4"), null,
                        List.of(op("a", "3"), op("b", "0")))))
                .singleElement().asString().contains("tiene que dar los 4 puntos");
        assertThat(ReglasDePuntos.puntuacionDeLaPregunta("P",
                new PreguntaAValidar("OPCION_UNICA", "x", n("4"), null,
                        List.of(op("a", "5"), op("b", "-1"), op("c", "4")))))
                .hasSize(2);
        assertThat(ReglasDePuntos.puntuacionDeLaPregunta("P",
                new PreguntaAValidar("OPCION_UNICA", "x", n("4"), null,
                        List.of(op("a", "4"), op("b", "0"))))).isEmpty();
    }

    @Test
    @DisplayName("Múltiple: cada opción entre −puntos y +puntos, y las positivas llegan al máximo")
    void laPuntuacionDeLaMultiple() {
        assertThat(ReglasDePuntos.puntuacionDeLaPregunta("P",
                new PreguntaAValidar("OPCION_MULTIPLE", "x", n("10"), null,
                        List.of(op("a", "6"), op("b", "6"), op("c", "-4"))))).isEmpty();
        assertThat(ReglasDePuntos.puntuacionDeLaPregunta("P",
                new PreguntaAValidar("OPCION_MULTIPLE", "x", n("10"), null,
                        List.of(op("a", "4"), op("b", "-11")))))
                .hasSize(2);
    }

    @Test
    @DisplayName("El total dice cuánto falta o sobra, y nada si suma exactamente 100")
    void elTotal() {
        assertThat(ReglasDePuntos.faltaDelTotal(95)).contains("faltan 5");
        assertThat(ReglasDePuntos.faltaDelTotal(105)).contains("sobran 5");
        assertThat(ReglasDePuntos.faltaDelTotal(100)).isNull();
    }
}
