package com.renaser.ai.ai_engine.organizacion.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Qué enlace vale en la presentación de una empresa (V81): la página web, las redes y los dos
 * videos, el de la empresa y el de cada puesto.
 *
 * <p>Las reglas son las de la spec, paso 4: solo {@code http} o {@code https}, con dominio, sin
 * usuario ni contraseña delante del dominio, sin espacios y hasta 500 caracteres. Un enlace sin
 * {@code http://} ni {@code https://} se completa con {@code https://} antes de comprobarlo
 * («renaser.pe» se guarda como «https://renaser.pe»), igual que hace el panel.
 *
 * <p>No se comprueba que la página exista: eso sería llamar a un tercero en cada guardado. Se
 * comprueba la forma, como en los enlaces del perfil del candidato ({@code ValidacionEnlaces}).
 *
 * <p>El panel tiene la misma regla en {@code src/dominio/presentacionEmpresa.ts}, con las mismas
 * palabras: cambiarla es cambiarla en los dos, con su caso en las dos pruebas.
 */
public final class EnlacesDeLaEmpresa {

    /** El tope de cualquier enlace, ya completado con https://. */
    public static final int MAXIMO = 500;

    public static final String MENSAJE_ENLACE = "Escribe una dirección web que empiece por https://";
    public static final String MENSAJE_VIDEO = "Pega el enlace de un video de YouTube o de Vimeo";

    private EnlacesDeLaEmpresa() {
    }

    /**
     * Sin espacios en los bordes, vacío como nulo, y con {@code https://} delante si no dice
     * ningún esquema. Un esquema distinto ({@code javascript:}, {@code ftp:}) se deja tal cual,
     * para que la comprobación lo rechace en vez de esconderlo detrás de un https://.
     */
    public static String normalizar(String crudo) {
        if (crudo == null) {
            return null;
        }
        String limpio = crudo.strip();
        if (limpio.isEmpty()) {
            return null;
        }
        return tieneEsquema(limpio) ? limpio : "https://" + limpio;
    }

    /**
     * Si empieza por un esquema: letras, cifras, «+» o «-» antes de los primeros dos puntos,
     * empezando por letra. Sin punto a propósito: «renaser.pe:8080» es un dominio con puerto, no
     * un esquema. Sin expresión regular: se recorre a mano.
     */
    static boolean tieneEsquema(String texto) {
        int dosPuntos = texto.indexOf(':');
        if (dosPuntos <= 0 || !esLetraAscii(texto.charAt(0))) {
            return false;
        }
        for (int i = 1; i < dosPuntos; i++) {
            char c = texto.charAt(i);
            if (!esLetraAscii(c) && !(c >= '0' && c <= '9') && c != '+' && c != '-') {
                return false;
            }
        }
        return true;
    }

    private static boolean esLetraAscii(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    /**
     * El enlace leído como dirección web, o nulo si no vale: ni {@code http} ni {@code https},
     * sin dominio (o un dominio sin punto), con usuario delante del dominio, con espacios o de
     * más de {@link #MAXIMO} caracteres.
     */
    public static URI comoDireccion(String enlace) {
        if (enlace == null || enlace.isEmpty() || enlace.length() > MAXIMO) {
            return null;
        }
        for (int i = 0; i < enlace.length(); i++) {
            char c = enlace.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) {
                return null;
            }
        }
        final URI uri;
        try {
            uri = new URI(enlace);
        } catch (URISyntaxException e) {
            return null;
        }
        String esquema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!esquema.equals("http") && !esquema.equals("https")) {
            return null;
        }
        String host = uri.getHost();
        if (uri.getRawUserInfo() != null || host == null || host.indexOf('.') < 0
                || host.startsWith(".") || host.endsWith(".")) {
            return null;
        }
        return uri;
    }

    /** El error de la página web, o nulo si vale o está vacía. Recibe el enlace ya normalizado. */
    public static String errorDelEnlace(String normalizado) {
        return normalizado == null || comoDireccion(normalizado) != null ? null : MENSAJE_ENLACE;
    }

    /** El error de una red: que no sea un enlace, o que no sea de su dominio. */
    public static String errorDeLaRed(RedSocial red, String normalizado) {
        if (normalizado == null) {
            return null;
        }
        URI uri = comoDireccion(normalizado);
        if (uri == null) {
            return MENSAJE_ENLACE;
        }
        return red.esSuyo(uri.getHost()) ? null : "Este enlace no es de " + red.nombre();
    }

    /**
     * El error de un video, el de la empresa o el de un puesto: que no sea un enlace, o que no sea
     * un video de YouTube o de Vimeo del que se pueda sacar cuál es.
     */
    public static String errorDelVideo(String normalizado) {
        if (normalizado == null) {
            return null;
        }
        if (comoDireccion(normalizado) == null) {
            return MENSAJE_ENLACE;
        }
        return VideoDePresentacion.leer(normalizado).isPresent() ? null : MENSAJE_VIDEO;
    }
}
