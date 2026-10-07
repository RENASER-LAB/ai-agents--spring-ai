package com.renaser.ai.ai_engine.perfilintegral.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Las reglas de las preguntas propias de una vacante (método PUNTOS), en un solo sitio.
 *
 * <p>Las usan cuatro caminos que tienen que decir lo mismo: guardar una pregunta en el
 * borrador, publicar la versión, cambiar los puntos con candidatos dentro y validar lo que
 * propone la IA. Si cada uno llevara su copia, un día el editor dejaría guardar algo que la
 * publicación rechaza por otra razón.
 *
 * <p>Dos niveles, a propósito:
 * <ul>
 *   <li><b>La forma</b> ({@link #formaDeLaPregunta}): lo que no puede guardarse nunca —un
 *       enunciado vacío, una cerrada con una sola opción, puntos con decimales—. Se exige al
 *       guardar, con un 400.
 *   <li><b>La puntuación</b> ({@link #puntuacionDeLaPregunta}): lo que hace falta para que
 *       la nota tenga sentido —que alguna opción dé el máximo—. Se exige al publicar: un
 *       borrador se escribe por partes y a medias todavía no cuadra.
 * </ul>
 *
 * <p><b>Los puntos son enteros</b> (decisión del 30/09/2026): así «suma exactamente 100» es
 * una cuenta sin redondeos. La nota que pone la IA sí lleva decimales, pero esa no pasa por
 * aquí.
 */
public final class ReglasDePuntos {

    public static final String ABIERTA = "ABIERTA";
    public static final String OPCION_UNICA = "OPCION_UNICA";
    public static final String OPCION_MULTIPLE = "OPCION_MULTIPLE";
    public static final String ESCALA = "ESCALA";

    /** Los cuatro tipos de las preguntas propias. Los 15 del banco de RENASER no entran. */
    public static final Set<String> TIPOS = Set.of(ABIERTA, OPCION_UNICA, OPCION_MULTIPLE, ESCALA);

    public static final int TOTAL = 100;
    public static final int MAXIMO_GUIA = 2000;
    public static final int MAXIMO_TEXTO_IA = 1000;

    private static final BigDecimal CIEN = BigDecimal.valueOf(TOTAL);

    /** Más allá de esto, lo escrito no se suma: ningún punto vale tanto y la suma no se desborda. */
    private static final BigDecimal TECHO_DE_LO_ESCRITO = BigDecimal.valueOf(1_000_000);

    private ReglasDePuntos() {
    }

    /** Una pregunta tal como hay que validarla, venga del borrador, de un cambio o de la IA. */
    public record PreguntaAValidar(String tipo, String enunciado, BigDecimal puntos,
                                   String queDebeTener, List<OpcionAValidar> opciones) {
        public List<OpcionAValidar> opcionesONada() {
            return opciones == null ? List.of() : opciones;
        }
    }

    /** Una opción (o un nivel de la escala). En la escala, el texto es el rótulo y es opcional. */
    public record OpcionAValidar(String texto, BigDecimal puntos) {
    }

    public static boolean esCerrada(String tipo) {
        return OPCION_UNICA.equals(tipo) || OPCION_MULTIPLE.equals(tipo) || ESCALA.equals(tipo);
    }

    /**
     * Lo que impide guardar una pregunta, dicho entero. Vacío si se puede guardar.
     *
     * @param donde cómo se nombra la pregunta en los mensajes («La pregunta», «La pregunta 3
     *              («¿Qué libro…»)»)
     */
    public static List<String> formaDeLaPregunta(String donde, PreguntaAValidar p) {
        List<String> faltas = new ArrayList<>();
        if (p.tipo() == null || !TIPOS.contains(p.tipo())) {
            faltas.add(donde + ": el tipo tiene que ser abierta, opción única, opción múltiple "
                    + "o escala.");
            return faltas;
        }
        if (p.enunciado() == null || p.enunciado().isBlank()) {
            faltas.add(donde + ": falta el enunciado.");
        }
        if (p.puntos() == null) {
            faltas.add(donde + ": faltan sus puntos.");
        } else if (!esEntero(p.puntos())) {
            faltas.add(donde + ": los puntos tienen que ser enteros, sin decimales.");
        } else if (p.puntos().signum() < 0 || p.puntos().compareTo(CIEN) > 0) {
            faltas.add(donde + ": los puntos van de 0 a 100.");
        }
        if (p.queDebeTener() != null && p.queDebeTener().length() > MAXIMO_TEXTO_IA) {
            faltas.add(donde + ": «qué debe tener una buena respuesta» admite hasta "
                    + MAXIMO_TEXTO_IA + " caracteres.");
        }
        if (!esCerrada(p.tipo())) {
            return faltas;
        }

        List<OpcionAValidar> opciones = p.opcionesONada();
        boolean escala = ESCALA.equals(p.tipo());
        int minimo = escala ? 3 : 2;
        if (opciones.size() < minimo || opciones.size() > 10) {
            faltas.add(donde + (escala
                    ? ": una escala tiene entre 3 y 10 niveles."
                    : ": una pregunta cerrada tiene entre 2 y 10 opciones."));
        }
        int n = 1;
        for (OpcionAValidar o : opciones) {
            String cual = escala ? "el nivel " + n : "la opción " + n;
            if (!escala && (o.texto() == null || o.texto().isBlank())) {
                faltas.add(donde + ": " + cual + " no tiene texto.");
            }
            if (o.puntos() == null) {
                faltas.add(donde + ": " + cual + " no tiene puntos.");
            } else if (!esEntero(o.puntos())) {
                faltas.add(donde + ": los puntos de " + cual + " tienen que ser enteros.");
            } else if (o.puntos().abs().compareTo(CIEN) > 0) {
                faltas.add(donde + ": los puntos de " + cual + " van de −100 a 100.");
            }
            n++;
        }
        return faltas;
    }

    /**
     * Lo que hace falta, además de la forma, para que la nota de la pregunta tenga sentido
     * (la tabla del punto 5 de la spec). Se exige al publicar y al cambiar los puntos.
     */
    public static List<String> puntuacionDeLaPregunta(String donde, PreguntaAValidar p) {
        List<String> faltas = new ArrayList<>();
        if (!esCerrada(p.tipo()) || p.puntos() == null) {
            return faltas;
        }
        BigDecimal maximo = p.puntos();
        List<BigDecimal> puntos = p.opcionesONada().stream()
                .map(OpcionAValidar::puntos).filter(Objects::nonNull).toList();
        if (puntos.isEmpty()) {
            return faltas;
        }
        String deLaPregunta = maximo.stripTrailingZeros().toPlainString();

        if (OPCION_MULTIPLE.equals(p.tipo())) {
            if (puntos.stream().anyMatch(x -> x.abs().compareTo(maximo) > 0)) {
                faltas.add(donde + ": cada opción tiene que valer entre −" + deLaPregunta
                        + " y " + deLaPregunta + ".");
            }
            BigDecimal positivas = puntos.stream().filter(x -> x.signum() > 0)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (positivas.compareTo(maximo) < 0) {
                faltas.add(donde + ": marcando todas las opciones buenas no se llega a sus "
                        + deLaPregunta + " puntos.");
            }
            return faltas;
        }

        // Opción única y escala: se lleva los puntos de lo que elija.
        String queCosa = ESCALA.equals(p.tipo()) ? "ningún nivel" : "ninguna opción";
        if (puntos.stream().anyMatch(x -> x.signum() < 0)) {
            faltas.add(donde + ": " + queCosa + " puede tener puntos negativos.");
        }
        if (puntos.stream().anyMatch(x -> x.compareTo(maximo) > 0)) {
            faltas.add(donde + ": " + queCosa + " puede pasar de los " + deLaPregunta
                    + " puntos de la pregunta.");
        }
        if (puntos.stream().noneMatch(x -> x.compareTo(maximo) == 0)) {
            faltas.add(donde + ": " + queCosa.replace("ningún", "algún").replace("ninguna", "alguna")
                    + " tiene que dar los " + deLaPregunta + " puntos de la pregunta.");
        }
        return faltas;
    }

    /** El mensaje del total cuando no cuadra, o nulo si suma exactamente 100. */
    public static String faltaDelTotal(int suma) {
        if (suma == TOTAL) {
            return null;
        }
        return suma < TOTAL
                ? "Los puntos suman " + suma + " de 100: faltan " + (TOTAL - suma) + "."
                : "Los puntos suman " + suma + " de 100: sobran " + (suma - TOTAL) + ".";
    }

    /**
     * Lo escrito, como entero, para sumarlo al total; vacío si no es un entero (o es tan
     * grande que no cabe en una suma). Entonces no hay suma que decir: la falta de ese campo
     * ya dice qué está mal, y una suma que lo dejara fuera contradiría la que se ve escrita en
     * el formulario (QA-11).
     */
    public static OptionalInt paraLaSuma(BigDecimal escrito) {
        if (!esEntero(escrito) || escrito.abs().compareTo(TECHO_DE_LO_ESCRITO) > 0) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(escrito.intValue());
    }

    /**
     * Cómo se nombra una pregunta en un mensaje: su posición y el principio del enunciado,
     * para que quien lee la lista sepa cuál es sin buscarla.
     */
    public static String nombreDe(int posicion, String enunciado) {
        String texto = enunciado == null ? "" : enunciado.strip();
        if (texto.isEmpty()) {
            return "La pregunta " + posicion;
        }
        String corto = texto.length() <= 40 ? texto : texto.substring(0, 39) + "…";
        return "La pregunta " + posicion + " («" + corto + "»)";
    }

    public static boolean esEntero(BigDecimal valor) {
        return valor != null && valor.stripTrailingZeros().scale() <= 0;
    }

    /**
     * Los puntos de una cerrada, calculados por el sistema (la IA nunca pone la nota de una
     * cerrada).
     *
     * <ul>
     *   <li>Opción única y escala: los puntos de lo elegido.
     *   <li>Opción múltiple: la suma de lo marcado, sin bajar de 0 ni pasar de los puntos de
     *       la pregunta.
     *   <li>Sin responder: 0.
     * </ul>
     *
     * @param elegida  la opción marcada en opción única y escala; nula si no respondió
     * @param marcadas los puntos de cada opción marcada en la múltiple
     */
    public static BigDecimal puntosDeCerrada(String tipo, int maximo, BigDecimal elegida,
                                             Collection<BigDecimal> marcadas) {
        if (OPCION_MULTIPLE.equals(tipo)) {
            if (marcadas == null || marcadas.isEmpty()) {
                return BigDecimal.ZERO;
            }
            BigDecimal suma = marcadas.stream().filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return acotar(suma, BigDecimal.valueOf(maximo));
        }
        if (elegida == null) {
            return BigDecimal.ZERO;
        }
        return acotar(elegida, BigDecimal.valueOf(maximo));
    }

    /** Entre 0 y el máximo. */
    public static BigDecimal acotar(BigDecimal valor, BigDecimal maximo) {
        if (valor.signum() < 0) {
            return BigDecimal.ZERO;
        }
        return valor.compareTo(maximo) > 0 ? maximo : valor;
    }
}
