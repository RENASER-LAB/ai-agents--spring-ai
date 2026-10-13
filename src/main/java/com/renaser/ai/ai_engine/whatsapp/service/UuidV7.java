package com.renaser.ai.ai_engine.whatsapp.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/**
 * Un UUID versión 7 (RFC 9562): los 48 primeros bits son los milisegundos Unix y el resto, azar.
 *
 * <p>Es lo que pide el contrato para el {@code avisoId}: ordenado por tiempo, así que el bot lo
 * indexa bien, y único sin coordinarse con nadie. El JDK solo trae la versión 4.
 */
public final class UuidV7 {

    private static final SecureRandom AZAR = new SecureRandom();

    private UuidV7() {}

    public static UUID nuevo() {
        return en(Instant.now());
    }

    static UUID en(Instant momento) {
        long milisegundos = momento.toEpochMilli() & 0xFFFF_FFFF_FFFFL;
        long azarAlto = AZAR.nextLong();
        long azarBajo = AZAR.nextLong();
        // 48 bits de tiempo · 4 de versión (0111) · 12 de azar
        long alto = (milisegundos << 16) | 0x7000L | (azarAlto & 0x0FFFL);
        // 2 de variante (10) · 62 de azar
        long bajo = (azarBajo & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(alto, bajo);
    }
}
