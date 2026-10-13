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
    private static final String RUTA = "/api/v1/integraciones/whatsapp/novedades";

    @Test
    @DisplayName("Es el HMAC-SHA256 en hexadecimal de «marca.ruta.cuerpo», los vectores conocidos")
    void vectorConocido() {
        // Calculados aparte con:
        //   printf '%s' '1760000000./api/v1/integraciones/whatsapp/novedades.{"telefono":"+51987654321"}' |
        //     openssl dgst -sha256 -hmac 'un-secreto-de-pruebas-de-32-bytes!!'
        //   printf '%s' '1760000000./v1/ex/esperas.{"telefono":"+51987654321"}' | openssl dgst …
        // El bot lo calcula en Python; si los dos lados no dan este mismo valor, ninguna llamada
        // pasa.
        assertThat(FirmaWhatsapp.firmar(SECRETO, "1760000000", RUTA, CUERPO))
                .isEqualTo("281f8f90bf130653a7b1a2b886a020a8beed745f7a73850c5f44aabaaa1ee1fd");
        assertThat(FirmaWhatsapp.firmar(SECRETO, "1760000000", "/v1/ex/esperas", CUERPO))
                .isEqualTo("e029b47a383ecd10d71d15b33c036b4a96a61da298ee2433c4d70dec27d45fdf");
    }

    @Test
    @DisplayName("AC-14 · la firma de otra ruta no vale, aunque el cuerpo y la marca sean los mismos")
    void laRutaCuenta() {
        String deBajas = FirmaWhatsapp.firmar(SECRETO, "1760000000",
                "/api/v1/integraciones/whatsapp/bajas", CUERPO);
        assertThat(FirmaWhatsapp.esValida(SECRETO, "1760000000", deBajas, RUTA, CUERPO, AHORA)).isFalse();
        assertThat(FirmaWhatsapp.esValida(SECRETO, "1760000000", deBajas,
                "/api/v1/integraciones/whatsapp/bajas", CUERPO, AHORA)).isTrue();
    }

    @Test
    @DisplayName("La propia firma vale, también en mayúsculas")
    void laPropiaVale() {
        String firma = FirmaWhatsapp.firmar(SECRETO, "1760000000", RUTA, CUERPO);
        assertThat(FirmaWhatsapp.esValida(SECRETO, "1760000000", firma, RUTA, CUERPO, AHORA)).isTrue();
        assertThat(FirmaWhatsapp.esValida(SECRETO, "1760000000", firma.toUpperCase(), RUTA, CUERPO, AHORA))
                .isTrue();
    }

    @Test
    @DisplayName("AC-14 · sin firma, con firma mala, con otro cuerpo o con otro secreto: no vale")
    void loQueNoVale() {
        String firma = FirmaWhatsapp.firmar(SECRETO, "1760000000", RUTA, CUERPO);
        assertThat(FirmaWhatsapp.esValida(SECRETO, "1760000000", null, RUTA, CUERPO, AHORA)).isFalse();
        assertThat(FirmaWhatsapp.esValida(SECRETO, null, firma, RUTA, CUERPO, AHORA)).isFalse();
        assertThat(FirmaWhatsapp.esValida(SECRETO, "1760000000", "00" + firma.substring(2), RUTA, CUERPO, AHORA))
                .isFalse();
        assertThat(FirmaWhatsapp.esValida(SECRETO, "1760000000", firma, RUTA,
                "{\"telefono\":\"+51900000000\"}".getBytes(StandardCharsets.UTF_8), AHORA)).isFalse();
        assertThat(FirmaWhatsapp.esValida("otro-secreto-distinto-de-32-bytes!!", "1760000000", firma,
                RUTA, CUERPO, AHORA)).isFalse();
        assertThat(FirmaWhatsapp.esValida(SECRETO, "no-es-un-numero", firma, RUTA, CUERPO, AHORA)).isFalse();
    }

    @Test
    @DisplayName("AC-14 · una marca de más de cinco minutos, hacia atrás o hacia delante, no vale")
    void laMarcaCaduca() {
        String haceCinco = String.valueOf(AHORA.getEpochSecond() - 300);
        String haceSeis = String.valueOf(AHORA.getEpochSecond() - 301);
        String dentroDeSeis = String.valueOf(AHORA.getEpochSecond() + 301);
        assertThat(FirmaWhatsapp.esValida(SECRETO, haceCinco,
                FirmaWhatsapp.firmar(SECRETO, haceCinco, RUTA, CUERPO), RUTA, CUERPO, AHORA)).isTrue();
        assertThat(FirmaWhatsapp.esValida(SECRETO, haceSeis,
                FirmaWhatsapp.firmar(SECRETO, haceSeis, RUTA, CUERPO), RUTA, CUERPO, AHORA)).isFalse();
        assertThat(FirmaWhatsapp.esValida(SECRETO, dentroDeSeis,
                FirmaWhatsapp.firmar(SECRETO, dentroDeSeis, RUTA, CUERPO), RUTA, CUERPO, AHORA)).isFalse();
    }

    @Test
    @DisplayName("Sin secreto configurado no vale ninguna: la puerta queda cerrada, no abierta")
    void sinSecretoNoValeNada() {
        String firma = FirmaWhatsapp.firmar(SECRETO, "1760000000", RUTA, CUERPO);
        assertThat(FirmaWhatsapp.esValida("", "1760000000", firma, RUTA, CUERPO, AHORA)).isFalse();
        assertThat(FirmaWhatsapp.esValida(null, "1760000000", firma, RUTA, CUERPO, AHORA)).isFalse();
    }
}
