package com.renaser.ai.ai_engine.ai.service.impl;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * La envoltura de una guía de calificación escrita por una empresa, antes de ir al
 * {@code system} de un agente.
 *
 * <p>Nació dentro de {@code AgentePruebaPuesto.conLaGuiaDeLaPrueba} y salió aquí cuando el
 * evaluador de las preguntas propias (V66) necesitó la misma defensa: <b>se reutiliza, no se
 * copia</b>. Dos copias de una defensa son dos sitios donde arreglarla, y el día que se
 * arregla una la otra queda abierta.
 *
 * <p>Tres cuidados, y ninguno es cosmético:
 * <ul>
 *   <li>Se recorta a {@link #MAXIMO_GUIA}: una guía larguísima es un intento de tapar la
 *       instrucción del agente por volumen, y recortar es mejor que dejar sin calificar.
 *   <li>El rótulo que la abre y la cierra lleva un identificador sorteado en cada
 *       calificación. Quien escribe la guía —días antes, sin verlo nunca— no puede cerrar su
 *       propio bloque y hacer pasar por instrucción del sistema lo que venga detrás.
 *   <li>Va anunciada: el rótulo de apertura dice de quién es y que es contenido, y el de
 *       cierre que lo que sigue manda. El formato de respuesta va siempre DESPUÉS.
 * </ul>
 *
 * <p>⚠️ Nada de esto es la red de seguridad de verdad: esa vive en el puente que guarda las
 * notas, que descarta lo ajeno, lo que no trae explicación y acota cada nota a su máximo.
 */
final class EnvolturaDeGuia {

    /** El cuarto tope del mismo número (DTO, CHECK de la base, servicio y este, al leer). */
    static final int MAXIMO_GUIA = 2000;

    private static final SecureRandom AZAR = new SecureRandom();

    private EnvolturaDeGuia() {
    }

    /**
     * La guía envuelta y el formato detrás. Sin guía, el formato solo: exactamente el mismo
     * prompt de siempre, sin una línea de más (lo que se manda se paga).
     *
     * @param abre   el rótulo de apertura, con un {@code %s} donde va la marca
     * @param cierra el rótulo de cierre, con un {@code %s} donde va la marca
     */
    static String envolver(String guia, String abre, String cierra, String formato) {
        String limpia = guia == null ? "" : guia.trim();
        if (limpia.isEmpty()) {
            return formato;
        }
        if (limpia.length() > MAXIMO_GUIA) {
            limpia = limpia.substring(0, MAXIMO_GUIA) + "\n[...cortada por lo larga]";
        }
        // Ni se recorta ni se sanea el texto: lo que lo acota es el rótulo irrepetible.
        String marca = new BigInteger(40, AZAR).toString(36);
        return abre.formatted(marca) + "\n\n" + limpia + "\n\n"
                + cierra.formatted(marca) + "\n\n" + formato;
    }
}
