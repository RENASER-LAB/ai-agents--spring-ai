package com.renaser.ai.ai_engine.perfil.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("La validación de enlaces del perfil (RF-166)")
class ValidacionEnlacesTest {

    @Test
    @DisplayName("Un LinkedIn tiene que ser de linkedin.com, con o sin subdominio")
    void linkedin() {
        assertThat(ValidacionEnlaces.esValida("LINKEDIN",
                "https://www.linkedin.com/in/camila")).isTrue();
        assertThat(ValidacionEnlaces.esValida("LINKEDIN",
                "https://linkedin.com/in/camila")).isTrue();
        assertThat(ValidacionEnlaces.esValida("LINKEDIN",
                "https://github.com/camila")).isFalse();
        // El dominio que TERMINA en linkedin.com pero no lo es: el truco clasico.
        assertThat(ValidacionEnlaces.esValida("LINKEDIN",
                "https://falsolinkedin.com/in/camila")).isFalse();
    }

    @Test
    @DisplayName("Un GitHub tiene que ser de github.com")
    void github() {
        assertThat(ValidacionEnlaces.esValida("GITHUB", "https://github.com/camila")).isTrue();
        assertThat(ValidacionEnlaces.esValida("GITHUB", "https://gitlab.com/camila")).isFalse();
    }

    @Test
    @DisplayName("Lo que no tiene forma de dirección no pasa, sea del tipo que sea")
    void formaDeUrl() {
        assertThat(ValidacionEnlaces.esValida("PORTAFOLIO", "mi portafolio bonito")).isFalse();
        assertThat(ValidacionEnlaces.esValida("PORTAFOLIO", "ftp://cosas.com/x")).isFalse();
        assertThat(ValidacionEnlaces.esValida("PORTAFOLIO", null)).isFalse();
        assertThat(ValidacionEnlaces.esValida("PORTAFOLIO", "   ")).isFalse();
        assertThat(ValidacionEnlaces.esValida("PORTAFOLIO", "https://micarpeta.dev")).isTrue();
    }
}
