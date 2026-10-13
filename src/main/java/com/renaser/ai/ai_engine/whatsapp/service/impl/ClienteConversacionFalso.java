package com.renaser.ai.ai_engine.whatsapp.service.impl;

import com.renaser.ai.ai_engine.whatsapp.service.ClienteConversacion;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Un bot de mentira, en memoria (modo {@code falso}): para desarrollo, las pruebas de
 * integración y los E2E.
 *
 * <p>Apunta cada llamada que recibe —anuncios, avisos y revocaciones— con el mismo cuerpo que
 * llevaría al bot de verdad, y las enseña por {@code GET /api/v1/integraciones/whatsapp/falso/registro}
 * (firmada como las demás). No manda nada a nadie: es lo que permite comprobar en un E2E que
 * llegó un solo aviso, o que cambiar el celular revocó el número anterior.
 *
 * <p>{@link #fallar(boolean)} lo pone a contestar como un bot caído, para probar los reintentos.
 */
@Slf4j
public class ClienteConversacionFalso implements ClienteConversacion {

    public static final String ESPERA = "ESPERA";
    public static final String AVISO = "AVISO";
    public static final String REVOCACION = "REVOCACION";

    /** Lo que se le pidió al bot, en el orden en que llegó. */
    public record Llamada(String tipo, Instant recibidaEn, Map<String, Object> cuerpo) {}

    /** Tope de memoria: un servidor de desarrollo encendido días no crece sin fin. */
    private static final int MAXIMO = 2000;

    private final List<Llamada> registro = new CopyOnWriteArrayList<>();
    private volatile boolean caido;

    @Override
    public void anunciar(Espera espera) {
        apuntar(ESPERA, espera.comoCuerpo());
    }

    @Override
    public void avisar(Aviso aviso) {
        apuntar(AVISO, aviso.comoCuerpo());
    }

    @Override
    public void revocar(Revocacion revocacion) {
        apuntar(REVOCACION, revocacion.comoCuerpo());
    }

    @Override
    public boolean estaEncendido() {
        return true;
    }

    /** Las llamadas recibidas, las más viejas primero. */
    public List<Llamada> registro() {
        return new ArrayList<>(registro);
    }

    /** Las de un número: en un E2E cada prueba mira solo lo suyo. */
    public List<Llamada> registroDe(String telefono) {
        return registro.stream()
                .filter(l -> telefono.equals(l.cuerpo().get("telefono")))
                .toList();
    }

    /** Para las pruebas: que conteste como si estuviera caído (reintentable) o que vuelva. */
    public void fallar(boolean estaCaido) {
        this.caido = estaCaido;
    }

    public void olvidar() {
        registro.clear();
    }

    private void apuntar(String tipo, Map<String, Object> cuerpo) {
        if (caido) {
            throw new FalloDelBot("El bot falso está caído", true);
        }
        if (registro.size() >= MAXIMO) {
            registro.remove(0);
        }
        registro.add(new Llamada(tipo, Instant.now(),
                java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(cuerpo))));
        log.info("Bot falso de WhatsApp: {} {}", tipo, cuerpo.get("postulacionRef"));
    }
}
