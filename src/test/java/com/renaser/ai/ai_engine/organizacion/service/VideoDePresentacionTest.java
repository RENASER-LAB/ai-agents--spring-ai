package com.renaser.ai.ai_engine.organizacion.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Qué videos valen y cómo se reducen")
class VideoDePresentacionTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.youtube.com/watch?v=abc123DEF45",
            "https://youtube.com/watch?v=abc123DEF45&t=30s",
            "https://m.youtube.com/watch?feature=share&v=abc123DEF45",
            "https://youtu.be/abc123DEF45?si=xyz",
            "https://www.youtube.com/shorts/abc123DEF45",
            "https://youtube.com/embed/abc123DEF45/",
            "http://www.youtu.be/abc123DEF45"})
    @DisplayName("las cuatro formas de YouTube, con o sin www. o m.")
    void youtube(String enlace) {
        assertThat(VideoDePresentacion.leer(enlace))
                .contains(new VideoDePresentacion("YOUTUBE", "abc123DEF45", null));
    }

    @Test
    @DisplayName("Vimeo con su número, y el oculto con su clave (AC-11)")
    void vimeo() {
        assertThat(VideoDePresentacion.leer("https://vimeo.com/123456789"))
                .contains(new VideoDePresentacion("VIMEO", "123456789", null));
        assertThat(VideoDePresentacion.leer("https://vimeo.com/123456789/abcdef1234"))
                .contains(new VideoDePresentacion("VIMEO", "123456789", "abcdef1234"));
        assertThat(VideoDePresentacion.leer("https://www.vimeo.com/123456789/abcdef1234?share=copy"))
                .contains(new VideoDePresentacion("VIMEO", "123456789", "abcdef1234"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.facebook.com/renaser/videos/123",
            "https://www.youtube.com/watch",
            "https://www.youtube.com/watch?v=",
            "https://www.youtube.com/watch?v=abc%22onload",
            "https://www.youtube.com/@renaser",
            "https://www.youtube.com/playlist?list=PL123456",
            "https://youtu.be/",
            "https://youtu.be/abc/def",
            "https://vimeo.com/renaser",
            "https://vimeo.com/123/abc/def",
            "https://vimeo.com/123/ab-cd",
            "https://drive.google.com/file/d/abc/view",
            "https://www.tiktok.com/@renaser/video/123",
            "javascript:alert(1)"})
    @DisplayName("lo demás no es un video del que se pueda sacar cuál es")
    void noValen(String enlace) {
        assertThat(VideoDePresentacion.leer(enlace)).isEmpty();
    }
}
