package com.renaser.ai.ai_engine.whatsapp.service;

import com.renaser.ai.ai_engine.postulacion.service.TextosDelMomento;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

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

    private static final String MIS_PROCESOS = "https://portal.renaser.pe/procesos";

    @Test
    @DisplayName("AC-23 · el «felicidades» de la prueba con el formato de la regla 19")
    void laPruebaDisponible() {
        Instant cierre = java.time.ZonedDateTime.now(TextosDeWhatsapp.ZONA).plusDays(2)
                .withHour(23).withMinute(59).withSecond(0).withNano(0).toInstant();
        String aviso = TextosDeWhatsapp.pruebaDisponible("Ana", "Asistente Contable", false, cierre,
                MIS_PROCESOS);

        assertThat(aviso).isEqualTo("¡Felicidades, Ana! 🎉\n\n"
                + "📌 *Asistente Contable*\n"
                + "Tu *prueba del puesto* ya está disponible.\n"
                + "⏰ Puedes empezarla hasta el *" + TextosDeWhatsapp.fechaYHora(cierre, Instant.now())
                + "* (hora de Lima).\n\n"
                + "👉 " + MIS_PROCESOS + "\n"
                + "Inicia sesión con tu correo y contraseña y abre «Mis procesos».");
        assertThat(TextosDeWhatsapp.fechaYHora(cierre, Instant.now())).endsWith("a las 23:59")
                .doesNotContain("T").doesNotContain("Z");
    }

    @Test
    @DisplayName("AC-23 · sin plazo conocido no hay línea del plazo; con cuestionario dice «prueba técnica»; sin nombre, «¡Felicidades!»")
    void laPruebaSinPlazo() {
        String aviso = TextosDeWhatsapp.pruebaDisponible(null, "Contador", true, null, MIS_PROCESOS);

        assertThat(aviso).startsWith("¡Felicidades! 🎉\n\n📌 *Contador*\nTu *prueba técnica* ya está disponible.")
                .doesNotContain("⏰").doesNotContain("Puedes empezarla")
                .endsWith("👉 " + MIS_PROCESOS + "\nInicia sesión con tu correo y contraseña y abre «Mis procesos».");
    }

    @Test
    @DisplayName("AC-23 · un avance cuenta lo que dice el portal de ese momento, con el formato de la regla 19")
    void unAvance() {
        TextosDelMomento.Texto momento = TextosDelMomento.de("SIMULACION_TURNO_CANDIDATO", null, false);
        String aviso = TextosDeWhatsapp.avance("Ana", "Contador", momento, MIS_PROCESOS);

        assertThat(aviso).isEqualTo("Hola, Ana 👋\n\n"
                + "📌 *Contador*\n"
                + "Te toca: *Confirma tu simulación*\n"
                + momento.texto() + "\n\n"
                + "👉 " + MIS_PROCESOS + "\n"
                + "Inicia sesión con tu correo y contraseña y abre «Mis procesos».");
        assertThat(TextosDeWhatsapp.avance(null, "Contador", momento, MIS_PROCESOS)).startsWith("Hola 👋\n\n");
    }

    @Test
    @DisplayName("AC-23 · con los textos al máximo el aviso no pasa de 1000 caracteres y conserva el enlace")
    void cabenEnMilCaracteres() {
        String nombre = "N".repeat(60);
        String vacante = "V".repeat(60);
        TextosDelMomento.Texto largo = new TextosDelMomento.Texto("T".repeat(80), "x".repeat(300));
        String enlace = "https://portal.renaser.pe/" + "p".repeat(274);

        String avance = TextosDeWhatsapp.avance(nombre, vacante, largo, enlace);
        String prueba = TextosDeWhatsapp.pruebaDisponible(nombre, vacante, false,
                Instant.now().plus(java.time.Duration.ofDays(1)), enlace);

        assertThat(avance).hasSizeLessThanOrEqualTo(1000).contains("👉 " + enlace);
        assertThat(prueba).hasSizeLessThanOrEqualTo(1000).contains("👉 " + enlace);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"http://portal.renaser.pe/procesos", "https://portal.renaser.pe/procesos?token=abc",
            "https://portal.renaser.pe/procesos#ana", "https://ana:clave@portal.renaser.pe/procesos",
            "/procesos", " "})
    @DisplayName("AC-23 · el enlace fijo solo vale en https, sin parámetros, fragmento ni credenciales")
    void elEnlaceFijo(String configurado) {
        assertThat(TextosDeWhatsapp.enlaceFijo(configurado)).isNull();
        // Y el aviso no lo pega: sale solo cómo entrar.
        assertThat(TextosDeWhatsapp.avance("Ana", "Contador",
                TextosDelMomento.de("SIMULACION_TURNO_CANDIDATO", null, false), configurado))
                .doesNotContain("👉").doesNotContain("token")
                .endsWith("Inicia sesión con tu correo y contraseña y abre «Mis procesos».");
    }

    @Test
    @DisplayName("Un plazo de otro año lleva el año; los asteriscos del título no rompen las negritas")
    void elAnoYLasNegritas() {
        Instant ahora = Instant.now();
        Instant elAnoQueViene = ahora.atZone(TextosDeWhatsapp.ZONA).plusYears(1).toInstant();
        assertThat(TextosDeWhatsapp.fechaYHora(elAnoQueViene, ahora))
                .contains("de " + elAnoQueViene.atZone(TextosDeWhatsapp.ZONA).getYear() + " a las");
        assertThat(TextosDeWhatsapp.avance("Ana", "*Contador* senior",
                TextosDelMomento.de("SIMULACION_TURNO_CANDIDATO", null, false), MIS_PROCESOS))
                .contains("📌 *Contador senior*\n");
    }
}
