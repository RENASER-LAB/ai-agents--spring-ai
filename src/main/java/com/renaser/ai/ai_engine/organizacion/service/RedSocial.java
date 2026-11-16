package com.renaser.ai.ai_engine.organizacion.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Las cinco redes que una empresa puede enlazar en su presentación (V81), cada una con los
 * dominios que se le aceptan. X no entra (decisión al redactar la spec).
 *
 * <p>El panel tiene la misma lista en {@code src/dominio/presentacionEmpresa.ts}, con los mismos
 * dominios y el mismo mensaje: cambiar una red es cambiarla en los dos.
 */
public enum RedSocial {
    FACEBOOK("Facebook", "facebook.com", "fb.com"),
    INSTAGRAM("Instagram", "instagram.com"),
    LINKEDIN("LinkedIn", "linkedin.com"),
    TIKTOK("TikTok", "tiktok.com"),
    YOUTUBE("YouTube", "youtube.com", "youtu.be");

    private final String nombre;
    private final List<String> dominios;

    RedSocial(String nombre, String... dominios) {
        this.nombre = nombre;
        this.dominios = List.of(dominios);
    }

    /** Cómo se llama en la pantalla: «Instagram». */
    public String nombre() {
        return nombre;
    }

    /** Si el host es de esta red: el dominio exacto o uno de sus subdominios (www., m.). */
    public boolean esSuyo(String host) {
        if (host == null) {
            return false;
        }
        String limpio = host.toLowerCase(Locale.ROOT);
        return dominios.stream().anyMatch(d -> limpio.equals(d) || limpio.endsWith("." + d));
    }

    /** La red de un código, sin mirar mayúsculas; vacío si no es una de las cinco. */
    public static Optional<RedSocial> deCodigo(String codigo) {
        if (codigo == null) {
            return Optional.empty();
        }
        for (RedSocial red : values()) {
            if (red.name().equalsIgnoreCase(codigo.strip())) {
                return Optional.of(red);
            }
        }
        return Optional.empty();
    }
}
