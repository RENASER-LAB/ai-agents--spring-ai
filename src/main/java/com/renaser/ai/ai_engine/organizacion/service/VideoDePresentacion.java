package com.renaser.ai.ai_engine.organizacion.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Un video de YouTube o de Vimeo, reducido a lo que hace falta para pintarlo (V81): el servicio,
 * el identificador del video y, en un video oculto de Vimeo, su clave.
 *
 * <p><b>Por qué se reduce.</b> Se guarda el enlace tal como se pegó, pero el portal nunca lo
 * copia dentro de la página: reconstruye el reproductor a partir de estas tres piezas
 * ({@code youtube-nocookie.com/embed/<id>} o {@code player.vimeo.com/video/<id>?h=<clave>}). Por
 * eso cada pieza solo admite letras, cifras, «_» y «-»: lo que llega a la página no puede llevar
 * nada más.
 *
 * <p>Valen, de YouTube: {@code youtube.com/watch?v=…}, {@code youtu.be/…},
 * {@code youtube.com/shorts/…} y {@code youtube.com/embed/…}, con o sin {@code www.} o {@code m.};
 * de Vimeo: {@code vimeo.com/<número>} y {@code vimeo.com/<número>/<clave>}.
 *
 * @param servicio      {@code YOUTUBE} o {@code VIMEO}
 * @param identificador el del video
 * @param clave         la del video oculto de Vimeo, o nulo
 */
public record VideoDePresentacion(String servicio, String identificador, String clave) {

    public static final String YOUTUBE = "YOUTUBE";
    public static final String VIMEO = "VIMEO";

    /** Lo que se acepta como identificador de YouTube: 6 a 64 letras, cifras, «_» o «-». */
    private static final int MINIMO_YOUTUBE = 6;
    private static final int MAXIMO_PIEZA = 64;
    /** Un número de Vimeo cabe de sobra en 15 cifras. */
    private static final int MAXIMO_NUMERO_VIMEO = 15;

    /** El video de un enlace, o vacío si no es uno de los que valen. */
    public static Optional<VideoDePresentacion> leer(String enlace) {
        URI uri = EnlacesDeLaEmpresa.comoDireccion(enlace);
        if (uri == null) {
            return Optional.empty();
        }
        List<String> tramos = tramosDe(uri.getRawPath());
        return switch (sinPrefijo(uri.getHost().toLowerCase(Locale.ROOT))) {
            case "youtu.be" -> tramos.size() == 1 ? deYoutube(tramos.get(0)) : Optional.empty();
            case "youtube.com" -> deYoutube(tramos, uri.getRawQuery());
            case "vimeo.com" -> deVimeo(tramos);
            default -> Optional.empty();
        };
    }

    private static Optional<VideoDePresentacion> deYoutube(List<String> tramos, String consulta) {
        if (tramos.size() == 1 && tramos.get(0).equals("watch")) {
            return deYoutube(parametro(consulta, "v"));
        }
        if (tramos.size() == 2 && (tramos.get(0).equals("shorts") || tramos.get(0).equals("embed"))) {
            return deYoutube(tramos.get(1));
        }
        return Optional.empty();
    }

    private static Optional<VideoDePresentacion> deYoutube(String id) {
        return esPieza(id, MINIMO_YOUTUBE, true)
                ? Optional.of(new VideoDePresentacion(YOUTUBE, id, null))
                : Optional.empty();
    }

    private static Optional<VideoDePresentacion> deVimeo(List<String> tramos) {
        if (tramos.isEmpty() || tramos.size() > 2 || !esNumero(tramos.get(0))) {
            return Optional.empty();
        }
        if (tramos.size() == 1) {
            return Optional.of(new VideoDePresentacion(VIMEO, tramos.get(0), null));
        }
        String clave = tramos.get(1);
        return esPieza(clave, 1, false)
                ? Optional.of(new VideoDePresentacion(VIMEO, tramos.get(0), clave))
                : Optional.empty();
    }

    /** «www.» o «m.» delante no cambian de qué servicio es. */
    private static String sinPrefijo(String host) {
        if (host.startsWith("www.")) {
            return host.substring(4);
        }
        return host.startsWith("m.") ? host.substring(2) : host;
    }

    private static List<String> tramosDe(String ruta) {
        List<String> tramos = new ArrayList<>();
        if (ruta == null) {
            return tramos;
        }
        for (String tramo : ruta.split("/")) {
            if (!tramo.isEmpty()) {
                tramos.add(tramo);
            }
        }
        return tramos;
    }

    private static String parametro(String consulta, String nombre) {
        if (consulta == null) {
            return null;
        }
        for (String par : consulta.split("&")) {
            if (par.startsWith(nombre + "=")) {
                return par.substring(nombre.length() + 1);
            }
        }
        return null;
    }

    /** Letras y cifras ASCII; con {@code conGuiones}, también «_» y «-». */
    private static boolean esPieza(String texto, int minimo, boolean conGuiones) {
        if (texto == null || texto.length() < minimo || texto.length() > MAXIMO_PIEZA) {
            return false;
        }
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            boolean alfanumerico = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9');
            if (!alfanumerico && !(conGuiones && (c == '_' || c == '-'))) {
                return false;
            }
        }
        return true;
    }

    private static boolean esNumero(String texto) {
        if (texto.isEmpty() || texto.length() > MAXIMO_NUMERO_VIMEO) {
            return false;
        }
        for (int i = 0; i < texto.length(); i++) {
            if (texto.charAt(i) < '0' || texto.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }
}
