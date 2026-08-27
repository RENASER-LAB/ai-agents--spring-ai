package com.renaser.ai.ai_engine.perfil.service;

import java.net.URI;
import java.util.Locale;

/**
 * La regla RF-166: un enlace tiene que tener forma de enlace, y el de LinkedIn tiene que
 * ser de linkedin.com y el de GitHub de github.com. Un enlace que no cumple no se guarda.
 *
 * <p>No se comprueba que la página exista — eso sería llamar a un tercero en cada guardado
 * y fallar cuando LinkedIn tenga un mal día. Se comprueba la forma, que es lo que evita el
 * error de pegar cualquier cosa en el campo equivocado.
 */
public final class ValidacionEnlaces {

    private ValidacionEnlaces() {
    }

    public static boolean esValida(String tipo, String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        final URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return false;
        }
        String esquema = uri.getScheme();
        String host = uri.getHost();
        if (esquema == null || host == null
                || !(esquema.equals("http") || esquema.equals("https"))) {
            return false;
        }
        String dominio = host.toLowerCase(Locale.ROOT);
        return switch (tipo) {
            case "LINKEDIN" -> dominio.equals("linkedin.com") || dominio.endsWith(".linkedin.com");
            case "GITHUB" -> dominio.equals("github.com") || dominio.endsWith(".github.com");
            default -> true;
        };
    }
}
