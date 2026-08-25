package com.renaser.ai.ai_engine.archivo.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * La huella de un archivo: SHA-256 en hexadecimal (64 caracteres).
 *
 * <p>Existe por una sola razón: saber que «este currículum ya se leyó» sin volver a pagar
 * la lectura al modelo. Dos archivos con la misma huella son el mismo contenido, venga por
 * la postulación que venga.
 */
public final class HashContenido {

    private HashContenido() {
    }

    public static String sha256(byte[] contenido) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] resumen = md.digest(contenido);
            StringBuilder hex = new StringBuilder(resumen.length * 2);
            for (byte b : resumen) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                   .append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 viene con el JDK; si falta, el entorno esta roto de verdad.
            throw new IllegalStateException("Este JDK no trae SHA-256", e);
        }
    }
}
