package com.renaser.ai.ai_engine.whatsapp.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("La firma de las llamadas con el bot (V73)")
class FirmaWhatsappTest {

    private static final String SECRETO = "un-secreto-de-pruebas-de-32-bytes!!";
    private static final byte[] CUERPO = "{\"telefono\":\"+51987654321\"}".getBytes(StandardCharsets.UTF_8);
    private static final Instant AHORA = Instant.ofEpochSecond(1_760_000_000L);

    @Test
    @DisplayName("Es el HMAC-SHA256 en hexadecimal de «marca.cuerpo», el vector conocido")
    void vectorConocido() {
        // Calculado aparte con: printf '1760000000.{"telefono":"+51987654321"}' |
        //   openssl dgst -sha256 -hmac 'un-secreto-de-pruebas-de-32-bytes!!'
        // El bot lo calcula en Python; si los dos lados no dan este mismo valor, ninguna llamada
        // pasa.
        assertThat(FirmaWhatsapp.firmar(SECRETO, "1760000000", CUERPO))
                .isEqualTo("ca8e8cc5655e95a1a9f28815a04f2fc18a5c56bd53f5ca9bdfecba6679ee9f2f");
    }

    @Test
    @DisplayName("La propia firma vale, también en mayúsculas")
    void laPropiaVale() {
        String firma = FirmaWhatsapp.firmar(SECRETO, "1760000000", CUERPO);
        assertThat(FirmaWhatsapp.esValida(SECRETO, "1760000000", firma, CUERPO, AHORA)).isTrue();
        assertThat(FirmaWhatsapp.esValida(SECRETO, "1760000000", firma.toUpperCase(), CUERPO, AHORA))
                .isTrue();
    }

    @Test
    @DisplayName("AC-14 · sin firma, con firma mala, con otro cuerpo o con otro secreto: no vale")
    void loQueNoVale() {
        String firma = FirmaWhatsapp.firmar(SECRETO, "1760000000", CUERPO);
        assertThat(FirmaWhatsapp.esValida(SECRETO, "1760000000", null, CUERPO, AHORA)).isFalse();
        assertThat(FirmaWhatsapp.esValida(SECRETO, null, firma, CUERPO, AHORA)).isFalse();
        assertThat(FirmaWhatsapp.esValida(SECRETO, "1760000000", "00" + firma.substring(2), CUERPO, AHORA))
                .isFalse();
        assertThat(FirmaWhatsapp.esValida(SECRETO, "1760000000", firma,
                "{\"telefono\":\"+51900000000\"}".getBytes(StandardCharsets.UTF_8), AHORA)).isFalse();
        assertThat(FirmaWhatsapp.esValida("otro-secreto-distinto-de-32-bytes!!", "1760000000", firma,
                CUERPO, AHORA)).isFalse();
        assertThat(FirmaWhatsapp.esValida(SECRETO, "no-es-un-numero", firma, CUERPO, AHORA)).isFalse();
    }

    @Test
    @DisplayName("AC-14 · una marca de más de cinco minutos, hacia atrás o hacia delante, no vale")
    void laMarcaCaduca() {
        String haceCinco = String.valueOf(AHORA.getEpochSecond() - 300);
        String haceSeis = String.valueOf(AHORA.getEpochSecond() - 301);
        String dentroDeSeis = String.valueOf(AHORA.getEpochSecond() + 301);
        assertThat(FirmaWhatsapp.esValida(SECRETO, haceCinco,
                FirmaWhatsapp.firmar(SECRETO, haceCinco, CUERPO), CUERPO, AHORA)).isTrue();
        assertThat(FirmaWhatsapp.esValida(SECRETO, haceSeis,
                FirmaWhatsapp.firmar(SECRETO, haceSeis, CUERPO), CUERPO, AHORA)).isFalse();
        assertThat(FirmaWhatsapp.esValida(SECRETO, dentroDeSeis,
                FirmaWhatsapp.firmar(SECRETO, dentroDeSeis, CUERPO), CUERPO, AHORA)).isFalse();
    }

    @Test
    @DisplayName("Sin secreto configurado no vale ninguna: la puerta queda cerrada, no abierta")
    void sinSecretoNoValeNada() {
        String firma = FirmaWhatsapp.firmar(SECRETO, "1760000000", CUERPO);
        assertThat(FirmaWhatsapp.esValida("", "1760000000", firma, CUERPO, AHORA)).isFalse();
        assertThat(FirmaWhatsapp.esValida(null, "1760000000", firma, CUERPO, AHORA)).isFalse();
    }
}
