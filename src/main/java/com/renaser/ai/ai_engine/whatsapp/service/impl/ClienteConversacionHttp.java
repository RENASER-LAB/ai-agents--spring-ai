package com.renaser.ai.ai_engine.whatsapp.service.impl;

import com.renaser.ai.ai_engine.whatsapp.service.ClienteConversacion;
import com.renaser.ai.ai_engine.whatsapp.service.FirmaWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.service.PropiedadesWhatsapp;

import tools.jackson.databind.JsonNode;
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
 * <p>Cada llamada lleva su firma ({@link FirmaWhatsapp}, sobre la marca, la ruta y el cuerpo) y
 * espera como mucho {@code app.whatsapp.segundos-de-espera}. Solo vale la respuesta del contrato
 * —204 en esperas y revocaciones, 202 en avisos—; cualquier otra es un fallo, que dice si se
 * reintenta ({@link FalloDelBot#seReintenta}) y con qué {@code code} lo explicó el bot. No
 * reintenta por su cuenta: lo decide quien la llama, que sabe si es un botón con alguien delante
 * o un sondeo que puede volver más tarde.
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
        mandar("/v1/ex/esperas", espera.comoCuerpo(), 204);
    }

    @Override
    public void avisar(Aviso aviso) {
        mandar("/v1/ex/avisos", aviso.comoCuerpo(), 202);
    }

    @Override
    public void revocar(Revocacion revocacion) {
        mandar("/v1/ex/revocaciones", revocacion.comoCuerpo(), 204);
    }

    @Override
    public boolean estaEncendido() {
        return true;
    }

    private void mandar(String ruta, Map<String, Object> cuerpo, int esperado) {
        byte[] bytes = JSON.writeValueAsBytes(cuerpo);
        String marca = String.valueOf(reloj.instant().getEpochSecond());
        URI destino = URI.create(base + ruta);
        // Se firma el path completo, como lo ve el bot: con el prefijo de la dirección base, si
        // lo hubiera, y sin host ni query.
        String rutaFirmada = destino.getRawPath();
        HttpRequest peticion = HttpRequest.newBuilder(destino)
                .timeout(espera)
                .header("Content-Type", "application/json")
                .header(FirmaWhatsapp.CABECERA_MARCA, marca)
                .header(FirmaWhatsapp.CABECERA_FIRMA,
                        FirmaWhatsapp.firmar(secreto, marca, rutaFirmada, bytes))
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
        if (estado == esperado) {
            return;
        }
        if (estado >= 200 && estado < 300) {
            // Hizo algo, pero no lo que dice el contrato: repetirlo no lo arregla.
            throw new FalloDelBot("El bot respondió " + estado + " en " + ruta + " y el contrato dice "
                    + esperado, false);
        }
        String codigo = codigoDelError(respuesta.body());
        throw new FalloDelBot("El bot respondió " + estado + (codigo == null ? "" : " " + codigo)
                + " en " + ruta + ": " + recortar(respuesta.body()), FalloDelBot.seReintenta(estado),
                codigo);
    }

    /** El {@code code} de un error del bot ({@code {code, message, …}}), o nulo si no lo trae. */
    static String codigoDelError(String cuerpo) {
        if (cuerpo == null || cuerpo.isBlank()) {
            return null;
        }
        try {
            JsonNode error = JSON.readTree(cuerpo);
            JsonNode code = error == null ? null : error.get("code");
            return code != null && code.isString() && !code.asString().isBlank() ? code.asString() : null;
        } catch (RuntimeException noEsJson) {
            return null;
        }
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
