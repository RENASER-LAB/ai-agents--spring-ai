package com.renaser.ai.ai_engine.whatsapp.service;

import java.util.Optional;

/**
 * El celular de la cuenta: cómo se escribe, cómo se guarda y cómo se enseña (V73).
 *
 * <p>Se guarda siempre en E.164 («+51987654321»). Lo que la persona escribe se acepta así:
 * <ul>
 *   <li>Perú: nueve cifras que empiezan por 9 («987 654 321»), con o sin el 51 delante.</li>
 *   <li>Otro país: el número completo con su prefijo, empezando por «+» o «00».</li>
 * </ul>
 * Los espacios, guiones, puntos y paréntesis no cuentan. Cualquier otra cosa no vale, con el
 * mismo mensaje que pinta el portal.
 *
 * <p>Una sola regla para el registro, «Mi cuenta» y la tarjeta de WhatsApp: si cada uno validara
 * por su cuenta, el portal dejaría pasar un número que la API rechaza.
 */
public final class Celular {

    /** Lo que se le dice a quien escribe uno que no vale. Es el mismo texto del portal. */
    public static final String MENSAJE =
            "Escribe un celular de 9 cifras que empiece por 9, o el número completo con su prefijo";

    private static final String PREFIJO_PERU = "+51";
    private static final int MINIMO_DE_CIFRAS = 8;
    private static final int MAXIMO_DE_CIFRAS = 15;

    private Celular() {}

    /**
     * Lo escrito, en E.164; vacío si no vale.
     *
     * <p>No lanza a propósito: quien la llama es una petición, y es ella la que lanza el
     * {@code IllegalArgumentException} con {@link #MENSAJE} —el 400 que el portal enseña junto al
     * campo— en su propio método.
     */
    public static Optional<String> comoE164(String escrito) {
        if (escrito == null) {
            return Optional.empty();
        }
        String limpio = sinSeparadores(escrito.trim());
        if (limpio.startsWith("00")) {
            limpio = "+" + limpio.substring(2);
        }
        if (limpio.startsWith("+")) {
            String cifras = limpio.substring(1);
            return esInternacional(cifras) ? Optional.of("+" + cifras) : Optional.empty();
        }
        if (soloCifras(limpio) && limpio.length() == 9 && limpio.charAt(0) == '9') {
            return Optional.of(PREFIJO_PERU + limpio);
        }
        // «51 987 654 321»: el peruano con su prefijo pero sin el «+».
        if (soloCifras(limpio) && limpio.length() == 11 && limpio.startsWith("519")) {
            return Optional.of("+" + limpio);
        }
        return Optional.empty();
    }

    /**
     * El número tal como lo manda el bot: E.164, con o sin el «+» (Meta lo manda sin él).
     *
     * <p>No se le aplica la regla de las nueve cifras: el bot siempre manda el número completo.
     */
    public static Optional<String> delBot(String numero) {
        if (numero == null) {
            return Optional.empty();
        }
        String limpio = sinSeparadores(numero.trim());
        String cifras = limpio.startsWith("+") ? limpio.substring(1) : limpio;
        return esInternacional(cifras) ? Optional.of("+" + cifras) : Optional.empty();
    }

    /** «+51 987 654 321» para Perú; los demás, tal cual se guardan. */
    public static String paraMostrar(String e164) {
        if (e164 == null) {
            return null;
        }
        if (esPeruano(e164)) {
            String n = e164.substring(3);
            return PREFIJO_PERU + " " + n.substring(0, 3) + " " + n.substring(3, 6) + " " + n.substring(6);
        }
        return e164;
    }

    /**
     * «+51 9•• ••• 321»: lo que se pinta cuando ya está conectado. Se ve que es su número sin
     * dejarlo entero en una pantalla que cualquiera puede mirar por encima del hombro.
     */
    public static String enmascarado(String e164) {
        if (e164 == null) {
            return null;
        }
        if (esPeruano(e164)) {
            String n = e164.substring(3);
            return PREFIJO_PERU + " " + n.charAt(0) + "•• ••• " + n.substring(6);
        }
        String cifras = e164.substring(1);
        int visiblesAlPrincipio = Math.min(3, cifras.length() - 3);
        return "+" + cifras.substring(0, visiblesAlPrincipio)
                + "•".repeat(cifras.length() - visiblesAlPrincipio - 3)
                + cifras.substring(cifras.length() - 3);
    }

    /** Lo que va en el enlace wa.me: solo cifras, sin el «+». */
    public static String paraEnlace(String e164) {
        return e164 == null ? null : e164.replace("+", "");
    }

    private static boolean esPeruano(String e164) {
        return e164.startsWith(PREFIJO_PERU) && e164.length() == 12;
    }

    private static boolean esInternacional(String cifras) {
        if (!soloCifras(cifras) || cifras.length() < MINIMO_DE_CIFRAS
                || cifras.length() > MAXIMO_DE_CIFRAS || cifras.charAt(0) == '0') {
            return false;
        }
        // Un peruano escrito con su prefijo sigue siendo un celular peruano: 51 y nueve cifras
        // que empiezan por 9. «+51 54 123456» es un fijo de Arequipa, y por WhatsApp no llega.
        if (cifras.startsWith("51")) {
            return cifras.length() == 11 && cifras.charAt(2) == '9';
        }
        return true;
    }

    private static boolean soloCifras(String texto) {
        if (texto.isEmpty()) {
            return false;
        }
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static String sinSeparadores(String texto) {
        StringBuilder limpio = new StringBuilder(texto.length());
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c != ' ' && c != '-' && c != '.' && c != '(' && c != ')' && c != '\t'
                    && c != ' ') {
                limpio.append(c);
            }
        }
        return limpio.toString();
    }
}
