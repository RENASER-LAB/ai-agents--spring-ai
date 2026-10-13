package com.renaser.ai.ai_engine.whatsapp.service;

import com.renaser.ai.ai_engine.postulacion.service.TextosDelMomento;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Lo que EX le da escrito al bot y al enlace (V73)")
class TextosDeWhatsappTest {

    @Test
    @DisplayName("AC-06 · el enlace abre el chat del bot con el mensaje del punto 7")
    void elEnlace() {
        String enlace = TextosDeWhatsapp.enlace("51928439930", "Ana", "Pérez", "Asistente Contable");

        URI uri = URI.create(enlace);
        assertThat(uri.getHost()).isEqualTo("wa.me");
        assertThat(uri.getPath()).isEqualTo("/51928439930");
        assertThat(uri.getRawQuery()).startsWith("text=").doesNotContain("+").doesNotContain(" ");
        assertThat(URLDecoder.decode(uri.getRawQuery().substring(5), StandardCharsets.UTF_8))
                .isEqualTo("Hola, soy Ana Pérez. Quiero saber cómo va mi postulación a Asistente Contable.");
    }

    @Test
    @DisplayName("El título de la vacante se corta a 60 caracteres")
    void elTituloSeCorta() {
        String largo = "Analista de planeamiento financiero y control de gestión para la región sur";
        String mensaje = TextosDeWhatsapp.mensaje("Ana", "Pérez", largo);

        String titulo = mensaje.substring(mensaje.indexOf(" a ") + 3, mensaje.length() - 1);
        assertThat(titulo).hasSizeLessThanOrEqualTo(60).endsWith("…");
        assertThat(largo).startsWith(titulo.substring(0, titulo.length() - 1));
        assertThat(TextosDeWhatsapp.mensaje("Ana", null, "Contador"))
                .isEqualTo("Hola, soy Ana. Quiero saber cómo va mi postulación a Contador.");
        assertThat(TextosDeWhatsapp.mensaje(null, null, null))
                .isEqualTo("Hola. Quiero saber cómo va mi postulación.");
    }

    @Test
    @DisplayName("El «felicidades» de la prueba, como el ejemplo del punto 10")
    void laPruebaDisponible() {
        assertThat(TextosDeWhatsapp.pruebaDisponible("Ana", "Asistente Contable", false))
                .isEqualTo("¡Felicidades, Ana! Tu prueba del puesto para Asistente Contable ya está "
                        + "disponible.\nEntra en el portal con tu correo y tu contraseña y abre "
                        + "«Mis procesos».");
        assertThat(TextosDeWhatsapp.pruebaDisponible("Ana", "Contador", true))
                .startsWith("¡Felicidades, Ana! Tu prueba técnica para Contador ya está disponible.");
    }

    @Test
    @DisplayName("Un avance cuenta lo que dice el portal de ese momento")
    void unAvance() {
        String aviso = TextosDeWhatsapp.avance("Ana", "Contador",
                TextosDelMomento.de("SIMULACION_TURNO_CANDIDATO", null, false));
        assertThat(aviso).startsWith("Hola, Ana. En tu postulación a Contador te toca: Confirma tu "
                + "simulación. Elige una de las fechas disponibles.");
        assertThat(aviso).endsWith("abre «Mis procesos».");
    }
}
