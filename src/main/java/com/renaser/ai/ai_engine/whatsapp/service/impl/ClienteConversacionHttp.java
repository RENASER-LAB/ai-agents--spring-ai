package com.renaser.ai.ai_engine.whatsapp.service.impl;

import com.renaser.ai.ai_engine.whatsapp.service.ClienteConversacion;
import com.renaser.ai.ai_engine.whatsapp.service.FirmaWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.service.PropiedadesWhatsapp;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;

/**
 * El bot de verdad: el servicio de conversación del CRM, por HTTPS (modo {@code real}).
 *
 * <p>Cada llamada lleva su firma ({@link FirmaWhatsapp}) y espera como mucho
 * {@code app.whatsapp.segundos-de-espera}. No reintenta por su cuenta: lo decide quien la llama,
 * que sabe si es un botón con alguien delante o un sondeo que puede volver más tarde.
 */
public class ClienteConversacionHttp implements ClienteConversacion {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http;
    private final String base;
    private final String secreto;
    private final Duration espera;
    private final Clock reloj;

    public ClienteConversacionHttp(PropiedadesWhatsapp propiedades, HttpClient http, Clock reloj) {
        this.base = sinBarraFinal(propiedades.getUrlServicio());
        this.secreto = propiedades.getSecretoHaciaElBot();
        this.espera = Duration.ofSeconds(Math.max(1, propiedades.getSegundosDeEspera()));
        this.http = http;
        this.reloj = reloj;
    }

    /** Lo que necesita el modo real para arrancar; sin ello no hay a quién ni con qué firmar. */
    public static void exigirConfiguracion(PropiedadesWhatsapp propiedades) {
        String url = propiedades.getUrlServicio();
        if (url == null || !url.trim().startsWith("https://")) {
            throw new IllegalStateException("app.whatsapp.modo=real exige app.whatsapp.url-servicio "
                    + "con https://: el bot solo se llama por HTTPS");
        }
        if (propiedades.getSecretoHaciaElBot() == null
                || propiedades.getSecretoHaciaElBot().length() < 32) {
            throw new IllegalStateException("app.whatsapp.modo=real exige "
                    + "app.whatsapp.secreto-hacia-el-bot de al menos 32 caracteres");
        }
    }

    @Override
    public void anunciar(Espera espera) {
        mandar("/v1/ex/esperas", espera.comoCuerpo());
    }

    @Override
    public void avisar(Aviso aviso) {
        mandar("/v1/ex/avisos", aviso.comoCuerpo());
    }

    @Override
    public void revocar(Revocacion revocacion) {
        mandar("/v1/ex/revocaciones", revocacion.comoCuerpo());
    }

    @Override
    public boolean estaEncendido() {
        return true;
    }

    private void mandar(String ruta, Map<String, Object> cuerpo) {
        byte[] bytes = JSON.writeValueAsBytes(cuerpo);
        String marca = String.valueOf(reloj.instant().getEpochSecond());
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(base + ruta))
                .timeout(espera)
                .header("Content-Type", "application/json")
                .header(FirmaWhatsapp.CABECERA_MARCA, marca)
                .header(FirmaWhatsapp.CABECERA_FIRMA, FirmaWhatsapp.firmar(secreto, marca, bytes))
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();
        HttpResponse<String> respuesta;
        try {
            respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new FalloDelBot("El bot no respondió en " + ruta + ": " + e.getMessage(), true, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FalloDelBot("Se interrumpió la llamada al bot en " + ruta, true, e);
        }
        int estado = respuesta.statusCode();
        if (estado >= 200 && estado < 300) {
            return;
        }
        boolean reintentable = estado >= 500 || estado == 429 || estado == 408;
        throw new FalloDelBot("El bot respondió " + estado + " en " + ruta + ": "
                + recortar(respuesta.body()), reintentable);
    }

    private static String recortar(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.length() > 300 ? texto.substring(0, 300) : texto;
    }

    private static String sinBarraFinal(String url) {
        String limpia = url == null ? "" : url.trim();
        while (limpia.endsWith("/")) {
            limpia = limpia.substring(0, limpia.length() - 1);
        }
        return limpia;
    }
}
