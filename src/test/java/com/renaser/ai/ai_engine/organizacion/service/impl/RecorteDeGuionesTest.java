package com.renaser.ai.ai_engine.organizacion.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** El código del cargo se recorta de guiones bajos por los dos lados, sin tocar los de en medio. */
class RecorteDeGuionesTest {

    @DisplayName("Quita los guiones de los extremos y deja vacío lo que solo eran guiones")
    @ParameterizedTest(name = "«{0}» → «{1}»")
    @CsvSource(value = {
            "_ABC_|ABC",
            "__A__B__|A__B",
            "___|''",
            "''|''",
            "ABC|ABC",
            "_|''",
            "A_|A",
            "_A|A"
    }, delimiter = '|')
    void recorta(String entrada, String esperado) {
        assertThat(ServicioEstructuraImpl.recortarGuiones(entrada)).isEqualTo(esperado);
    }
}
