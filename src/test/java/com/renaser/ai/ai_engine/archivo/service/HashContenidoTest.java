package com.renaser.ai.ai_engine.archivo.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("La huella de un archivo")
class HashContenidoTest {

    @Test
    @DisplayName("Es el SHA-256 de verdad, contrastado con un valor conocido")
    void valorConocido() {
        // sha256("abc"), de la especificacion FIPS 180-4. Si esto falla, no es un hash.
        assertThat(HashContenido.sha256("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    @DisplayName("El mismo contenido da la misma huella; uno distinto, otra")
    void estable() {
        byte[] uno = "curriculum de Camila".getBytes(StandardCharsets.UTF_8);
        assertThat(HashContenido.sha256(uno)).isEqualTo(HashContenido.sha256(uno.clone()));
        assertThat(HashContenido.sha256(uno))
                .isNotEqualTo(HashContenido.sha256("otro archivo".getBytes(StandardCharsets.UTF_8)))
                .hasSize(64);
    }
}
