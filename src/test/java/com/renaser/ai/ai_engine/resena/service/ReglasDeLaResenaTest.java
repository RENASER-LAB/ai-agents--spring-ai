package com.renaser.ai.ai_engine.resena.service;

import com.renaser.ai.ai_engine.resena.dto.DtosResena.Barra;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.ResumenResenas;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@DisplayName("Las reglas de las reseñas que no dependen de nadie")
class ReglasDeLaResenaTest {

    @Test
    @DisplayName("5, 5 y 4 promedian 4,7: un decimal, redondeado a la mitad hacia arriba")
    void elPromedioLlevaUnDecimal() {
        ResumenResenas resumen = ReglasDeLaResena.resumen(List.of(5, 5, 4));

        assertThat(resumen.promedio()).isEqualByComparingTo("4.7");
        assertThat(resumen.promedio().scale()).isEqualTo(1);
        assertThat(resumen.cantidad()).isEqualTo(3);
        assertThat(resumen.reparto()).containsExactly(new Barra(5, 2), new Barra(4, 1),
                new Barra(3, 0), new Barra(2, 0), new Barra(1, 0));
    }

    @Test
    @DisplayName("Con una sola reseña también hay promedio: «5,0 · 1 reseña», sin mínimo")
    void conUnaSolaTambien() {
        assertThat(ReglasDeLaResena.resumen(List.of(5)).promedio()).isEqualByComparingTo("5.0");
        assertThat(ReglasDeLaResena.promedio(9, 2)).isEqualByComparingTo("4.5");
    }

    @Test
    @DisplayName("Sin reseñas no hay promedio, y las cinco barras salen igual, en cero")
    void sinResenasNoHayPromedio() {
        ResumenResenas resumen = ReglasDeLaResena.resumen(List.of());

        assertThat(resumen.promedio()).isNull();
        assertThat(resumen.cantidad()).isZero();
        assertThat(resumen.reparto()).hasSize(5).allMatch(b -> b.cantidad() == 0);
    }

    @Test
    @DisplayName("Las estrellas son un entero de 1 a 5: 4,5, 0, 6 y ninguna se rechazan; 4,0 vale 4")
    void lasEstrellasSonUnEntero() {
        assertThat(ReglasDeLaResena.estrellasValidas(new BigDecimal("4.0"))).isEqualTo(4);
        assertThat(ReglasDeLaResena.estrellasValidas(BigDecimal.ONE)).isEqualTo(1);
        for (String mala : List.of("4.5", "0", "6", "-1")) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> ReglasDeLaResena.estrellasValidas(new BigDecimal(mala)));
        }
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ReglasDeLaResena.estrellasValidas(null))
                .withMessageContaining("estrellas");
    }

    @Test
    @DisplayName("El texto no cuenta los espacios de los extremos, y solo espacios es vacío")
    void elTextoSeCuentaSinLosExtremos() {
        String treinta = "a".repeat(30);
        assertThat(ReglasDeLaResena.textoValido("   " + treinta + "\n ", 30, 1000, "La opinión"))
                .isEqualTo(treinta);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ReglasDeLaResena.textoValido("  " + "a".repeat(29) + "  ",
                        30, 1000, "La opinión"))
                .withMessageContaining("al menos 30");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ReglasDeLaResena.textoValido("a".repeat(1001), 30, 1000,
                        "La opinión"))
                .withMessageContaining("hasta 1000");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ReglasDeLaResena.textoValido("      ", 30, 1000, "La opinión"))
                .withMessageContaining("obligatoria");
    }

    @Test
    @DisplayName("Un emoji cuenta como un carácter, igual que en la base")
    void unEmojiEsUnCaracter() {
        // 500 caracteres de verdad: con la cuenta de Java serían 1000 y se rechazaría.
        String quinientos = "👍".repeat(500);
        assertThat(ReglasDeLaResena.textoValido(quinientos, 30, 500, "Tu respuesta"))
                .isEqualTo(quinientos);
    }

    @Test
    @DisplayName("«Otro motivo» exige comentario; los demás no, y el comentario tiene tope")
    void otroMotivoExigeComentario() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ReglasDeLaResena.comentarioValido("OTRO", "   "));
        assertThat(ReglasDeLaResena.comentarioValido("OFENSIVA", null)).isNull();
        assertThat(ReglasDeLaResena.comentarioValido("OTRO", " algo ")).isEqualTo("algo");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ReglasDeLaResena.comentarioValido("FALSA", "x".repeat(501)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ReglasDeLaResena.motivoValido("INVENTADO"));
    }
}
