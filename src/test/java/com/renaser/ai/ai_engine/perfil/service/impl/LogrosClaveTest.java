package com.renaser.ai.ai_engine.perfil.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Cómo se limpian los logros clave al guardar y cómo van y vuelven de la columna jsonb.
 *
 * <p>Lo que se fija: el orden se respeta y los huecos se cierran (la caja 1 y la 3 quedan
 * como primero y segundo), los repetidos NO se deduplican, un salto de línea pegado es un
 * espacio, y los topes —tres logros, cien caracteres— se miden sobre lo que se guarda.
 */
@DisplayName("Los logros clave del perfil")
class LogrosClaveTest {

    @Test
    @DisplayName("Se recortan, se quitan los vacíos y se respeta el orden")
    void limpiaYConservaElOrden() {
        assertThat(LogrosClave.limpiar(Arrays.asList(" Uno ", null, "\tDos  ")))
                .containsExactly("Uno", "Dos");
        assertThat(LogrosClave.limpiar(Arrays.asList("", "Tres", "  "))).containsExactly("Tres");
    }

    @Test
    @DisplayName("Los repetidos se guardan tal cual")
    void noDeduplica() {
        assertThat(LogrosClave.limpiar(List.of("Igual", "Igual"))).containsExactly("Igual", "Igual");
    }

    @Test
    @DisplayName("Un salto de línea pegado se convierte en espacio")
    void saltosDeLineaSonEspacios() {
        assertThat(LogrosClave.limpiar(List.of("Reduje\r\nel cierre\nde 10\ra 4 días")))
                .containsExactly("Reduje el cierre de 10 a 4 días");
    }

    @Test
    @DisplayName("Cien caracteres entran, aunque lleguen con espacios alrededor")
    void cienEntran() {
        String cien = "x".repeat(100);

        assertThat(LogrosClave.limpiar(List.of("  " + cien + "  "))).containsExactly(cien);
    }

    @Test
    @DisplayName("Ciento uno no entran")
    void cientoUnoNo() {
        List<String> largo = List.of("x".repeat(101));

        assertThatIllegalArgumentException().isThrownBy(() -> LogrosClave.limpiar(largo))
                .withMessageContaining("100");
    }

    @Test
    @DisplayName("Cuatro no entran, aunque alguno venga vacío")
    void cuatroNo() {
        List<String> cuatro = List.of("a", "b", "c", "");

        assertThatIllegalArgumentException().isThrownBy(() -> LogrosClave.limpiar(cuatro))
                .withMessageContaining("3");
    }

    @Test
    @DisplayName("Sin ninguno la columna queda en null, y con alguno va y vuelve igual")
    void idaYVuelta() {
        assertThat(LogrosClave.aJson(List.of())).isNull();

        List<String> logros = List.of("Migré 40 servicios a AWS | sin caídas", "Dijo \"sí\"");
        assertThat(LogrosClave.deJson(LogrosClave.aJson(logros))).isEqualTo(logros);
    }

    @Test
    @DisplayName("Leer nunca da null: vacío, en blanco o ilegible se pintan como ninguno")
    void leerNuncaDaNull() {
        assertThat(LogrosClave.deJson(null)).isEmpty();
        assertThat(LogrosClave.deJson("  ")).isEmpty();
        assertThat(LogrosClave.deJson("null")).isEmpty();
        assertThat(LogrosClave.deJson("{\"no\":\"es una lista\"}")).isEmpty();
        assertThat(LogrosClave.deJson("[\"Uno\", null]")).containsExactly("Uno");
    }
}
