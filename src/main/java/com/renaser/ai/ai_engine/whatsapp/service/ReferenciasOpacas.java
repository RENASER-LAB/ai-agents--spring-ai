package com.renaser.ai.ai_engine.whatsapp.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.OptionalLong;

/**
 * Las referencias que la consulta v2 entrega al bot sin enseñar un id interno (V76): la de la
 * vacante y el cursor del listado.
 *
 * <p>Las dos son un HMAC con el secreto del bot ({@link FirmaWhatsapp}), así que no se pueden
 * fabricar ni recorrer: la de la vacante no deja adivinar su id, y el cursor está atado al número
 * que lo pidió. Un cursor de otro número, alterado o inventado no se lee (C-1).
 *
 * <p>Cambiar el secreto cambia las referencias: el bot no las guarda más allá de la conversación.
 */
public final class ReferenciasOpacas {

    /** Lo que mide como mucho un cursor (C-3). */
    public static final int LARGO_DEL_CURSOR = 200;

    private static final String PREFIJO_VACANTE = "vc_";
    private static final String PREFIJO_CURSOR = "c1.";
    private static final int CIFRAS = 32;
    /** Sin secreto la puerta del bot ya está cerrada (todo 401); esto solo evita una clave vacía. */
    private static final String SIN_SECRETO = "sin-secreto-configurado";

    private ReferenciasOpacas() {}

    /** «vc_» y 32 cifras hexadecimales: la misma vacante, la misma referencia. */
    public static String deLaVacante(String secreto, Long vacanteId) {
        return PREFIJO_VACANTE + huella(secreto, "vacante", String.valueOf(vacanteId));
    }

    /** El cursor que sigue después del vínculo {@code ultimo}, para ese número y ningún otro. */
    public static String cursor(String secreto, String telefono, long ultimo) {
        String posicion = Long.toString(ultimo, Character.MAX_RADIX);
        return PREFIJO_CURSOR + posicion + "." + huella(secreto, "cursor." + telefono, posicion);
    }

    /** Dónde sigue el listado, o vacío si el cursor no es de este número o no es un cursor. */
    public static OptionalLong leerCursor(String secreto, String telefono, String cursor) {
        if (cursor == null || !cursor.startsWith(PREFIJO_CURSOR) || cursor.length() > LARGO_DEL_CURSOR) {
            return OptionalLong.empty();
        }
        String resto = cursor.substring(PREFIJO_CURSOR.length());
        int punto = resto.indexOf('.');
        if (punto <= 0) {
            return OptionalLong.empty();
        }
        String posicion = resto.substring(0, punto);
        byte[] esperada = huella(secreto, "cursor." + telefono, posicion).getBytes(StandardCharsets.UTF_8);
        byte[] recibida = resto.substring(punto + 1).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(esperada, recibida)) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(posicion, Character.MAX_RADIX));
        } catch (NumberFormatException noEsUnNumero) {
            return OptionalLong.empty();
        }
    }

    /** ¿Tiene la forma de una referencia de vínculo de EX? «pw_» y 32 cifras hexadecimales. */
    public static boolean pareceReferencia(String referencia) {
        if (referencia == null || referencia.length() != 3 + CIFRAS || !referencia.startsWith("pw_")) {
            return false;
        }
        for (int i = 3; i < referencia.length(); i++) {
            char c = referencia.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    private static String huella(String secreto, String uso, String valor) {
        String clave = secreto == null || secreto.isBlank() ? SIN_SECRETO : secreto;
        return FirmaWhatsapp.firmar(clave, "ex-v2", uso, valor.getBytes(StandardCharsets.UTF_8))
                .substring(0, CIFRAS);
    }
}
