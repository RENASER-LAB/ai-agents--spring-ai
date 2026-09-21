package com.renaser.ai.ai_engine.notificacion.service;

import java.util.Set;

/**
 * Los textos de correo que el sistema ya no manda, y por tanto ya no se ofrecen para editar.
 *
 * <p><b>Por qué no basta con dejar de mandarlos.</b> Un texto que sigue en la pantalla de
 * configuración invita a trabajar en balde: alguien lo reescribe con cuidado, lo guarda, y
 * ese texto no sale nunca. Peor todavía, se queda creyendo que a los candidatos les llega un
 * correo que ya no existe.
 *
 * <p><b>Y por qué no se borran.</b> Los correos ya enviados guardan el código y la versión
 * del texto con el que salieron: borrar la plantilla dejaría sin explicar lo que se le dijo
 * a cada persona. Las filas se conservan intactas; lo único que se retira es la invitación a
 * seguir editándolas.
 *
 * <p>Hoy solo hay uno: {@code REMUNERACION_ACTUALIZADA}. Desde que las noticias de la
 * vacante salen únicamente por la campana del portal, ese correo no se manda.
 */
public final class TextosDeCorreoRetirados {

    private TextosDeCorreoRetirados() {}

    public static final Set<String> CODIGOS = Set.of("REMUNERACION_ACTUALIZADA");

    public static boolean estaRetirado(String codigo) {
        return codigo != null && CODIGOS.contains(codigo.trim());
    }

    /**
     * Frena lo que intente volver a poner en circulación un texto retirado.
     *
     * @throws IllegalArgumentException con la razón, para quien esté en la pantalla
     */
    public static void exigirQueSigaEnUso(String codigo) {
        if (estaRetirado(codigo)) {
            throw new IllegalArgumentException("El texto «" + codigo + "» ya no se manda por "
                    + "correo: lo que cambia en una vacante se avisa solo en el portal del "
                    + "candidato. Se conserva lo enviado, pero no se edita");
        }
    }
}
