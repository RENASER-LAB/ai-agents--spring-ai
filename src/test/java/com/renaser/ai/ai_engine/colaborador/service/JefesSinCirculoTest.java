package com.renaser.ai.ai_engine.colaborador.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Nadie acaba siendo jefe de su propio jefe")
class JefesSinCirculoTest {

    @Test
    @DisplayName("A reporta a B, y B a A cierra el círculo")
    void dosPersonas() {
        assertThat(JefesSinCirculo.creaCirculo("B", "A", Map.of("A", "B"))).isTrue();
    }

    @Test
    @DisplayName("un círculo largo también se ve, y ser su propio jefe es el más corto")
    void largoYPropio() {
        assertThat(JefesSinCirculo.creaCirculo("D", "A", Map.of("A", "B", "B", "C", "C", "D"))).isTrue();
        assertThat(JefesSinCirculo.creaCirculo("A", "A", Map.of())).isTrue();
    }

    @Test
    @DisplayName("una cadena normal no es un círculo, ni uno viejo ajeno lo hace entrar en bucle")
    void sinCirculo() {
        assertThat(JefesSinCirculo.creaCirculo("E", "A", Map.of("A", "B", "B", "C"))).isFalse();
        assertThat(JefesSinCirculo.creaCirculo("E", "A", Map.of("A", "B", "B", "A"))).isFalse();
        assertThat(JefesSinCirculo.creaCirculo("E", null, Map.of())).isFalse();
    }
}
