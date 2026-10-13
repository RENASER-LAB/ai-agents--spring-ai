package com.renaser.ai.ai_engine.whatsapp.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

/**
 * La firma de las llamadas entre EX y el bot, igual en las dos direcciones (V73).
 *
 * <p>Dos cabeceras: {@value #CABECERA_MARCA} con los segundos Unix del momento en que se manda,
 * y {@value #CABECERA_FIRMA} con el HMAC-SHA256, en hexadecimal, de {@code marca + "." + cuerpo}.
 * Se compara en tiempo constante y se rechaza una marca a más de cinco minutos de ahora, hacia
 * atrás o hacia delante: una llamada capturada no se puede repetir más tarde.
 *
 * <p>Un secreto por dirección: si uno se filtra, el otro lado sigue cerrado.
 */
public final class FirmaWhatsapp {

    public static final String CABECERA_MARCA = "X-Renaser-Marca";
    public static final String CABECERA_FIRMA = "X-Renaser-Firma";
    public static final long SEGUNDOS_DE_VENTANA = 300;

    private static final String ALGORITMO = "HmacSHA256";

    private FirmaWhatsapp() {}

    /** La firma de un cuerpo con su marca, en hexadecimal en minúsculas. */
    public static String firmar(String secreto, String marca, byte[] cuerpo) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), ALGORITMO));
            mac.update((marca + ".").getBytes(StandardCharsets.UTF_8));
            mac.update(cuerpo == null ? new byte[0] : cuerpo);
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo calcular la firma HMAC-SHA256", e);
        }
    }

    /**
     * Si la llamada viene firmada con este secreto y a tiempo.
     *
     * <p>Sin secreto configurado no vale ninguna: un despliegue que olvide el suyo cierra la
     * puerta, no la abre.
     */
    public static boolean esValida(String secreto, String marca, String firma, byte[] cuerpo,
                                   Instant ahora) {
        if (secreto == null || secreto.isBlank() || marca == null || firma == null) {
            return false;
        }
        long segundos;
        try {
            segundos = Long.parseLong(marca.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(ahora.getEpochSecond() - segundos) > SEGUNDOS_DE_VENTANA) {
            return false;
        }
        byte[] esperada = firmar(secreto, marca.trim(), cuerpo).getBytes(StandardCharsets.UTF_8);
        byte[] recibida = firma.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(esperada, recibida);
    }
}
