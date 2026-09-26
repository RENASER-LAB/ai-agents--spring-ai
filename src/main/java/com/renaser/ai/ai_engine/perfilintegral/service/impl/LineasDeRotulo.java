package com.renaser.ai.ai_engine.perfilintegral.service.impl;

/**
 * Cuántas líneas ocupa un rótulo con el ajuste de texto encendido, en una columna de un
 * ancho dado.
 *
 * <p><b>Por qué hace falta.</b> Con el ajuste de texto encendido el rótulo se parte en
 * líneas, pero la fila no crece sola: una fila escrita por código sin altura se abre con la
 * de una línea, en Excel y en LibreOffice, y el resto del rótulo queda escondido hasta que
 * alguien la agranda a mano. La altura tiene que ir escrita en el archivo, y para escribirla
 * hay que saber cuántas líneas hacen falta.
 *
 * <p><b>Por qué una estimación y no una medida.</b> Medir de verdad pide las métricas de la
 * letra, y eso es AWT: en un servidor sin fuentes instaladas falla, o mide con otra letra.
 * Aquí se cuenta con una tabla de anchos aproximados de Calibri en negrita —la letra de la
 * cabecera— expresados en la unidad con la que Excel mide las columnas, el ancho de una
 * cifra.
 *
 * <p>⚠️ <b>Se equivoca hacia arriba a propósito.</b> Los anchos de la tabla son los de las
 * letras más anchas de cada grupo, se descuenta el margen de la celda y solo se parte en los
 * espacios. Una línea de más es un poco de aire en la cabecera; una de menos es un rótulo
 * cortado, que es justo lo que esto existe para evitar.
 */
final class LineasDeRotulo {

    /**
     * Lo que se come el margen interior de la celda, en anchos de cifra.
     *
     * <p>Excel deja unos píxeles a cada lado y LibreOffice algo parecido: sin descontarlos,
     * un rótulo que cabe justo en la cuenta sale partido en pantalla.
     */
    private static final double MARGEN_DE_CELDA = 1.0;

    private LineasDeRotulo() {
    }

    /**
     * Las líneas que necesita {@code rotulo} en una columna de {@code anchoEnCaracteres}
     * —el mismo número que se le da a {@code setColumnWidth}, sin el ×256—, sin pasar de
     * {@code tope}.
     *
     * <p>Nunca menos de una: una celda vacía también ocupa su línea. Se para en cuanto llega
     * al tope, así que un rótulo de treinta mil caracteres no cuesta más que uno de cien.
     */
    static int cuantas(String rotulo, int anchoEnCaracteres, int tope) {
        if (tope < 1) {
            throw new IllegalArgumentException("El tope de líneas tiene que ser al menos 1: " + tope);
        }
        String texto = rotulo == null ? "" : rotulo;
        double cabe = Math.max(1.0, anchoEnCaracteres - MARGEN_DE_CELDA);
        int lineas = 0;
        // Un salto de línea escrito en el rótulo empieza otra línea, quepa o no la anterior.
        for (String parrafo : texto.split("\n", -1)) {
            lineas += lineasDeUnParrafo(parrafo, cabe, tope - lineas);
            if (lineas >= tope) {
                return tope;
            }
        }
        return Math.max(1, lineas);
    }

    /**
     * El ajuste de texto de siempre: palabra a palabra mientras quepa, y la que no cabe
     * entera en una línea vacía se parte donde se acabe el sitio —igual que hacen Excel y
     * LibreOffice con una palabra más larga que la columna—.
     */
    private static int lineasDeUnParrafo(String parrafo, double cabe, int quedan) {
        int lineas = 1;
        double ocupado = 0;
        boolean vacia = true;
        for (String palabra : parrafo.split(" ", -1)) {
            double ancho = anchoDe(palabra);
            if (!vacia && ocupado + ESPACIO + ancho <= cabe) {
                ocupado += ESPACIO + ancho;
                continue;
            }
            if (!vacia) {
                lineas++;
            }
            // La palabra empieza línea. Si no cabe ni sola, se parte en tantas como pida.
            while (ancho > cabe) {
                lineas++;
                ancho -= cabe;
                if (lineas > quedan) {
                    return lineas;
                }
            }
            ocupado = ancho;
            vacia = false;
            if (lineas > quedan) {
                return lineas;
            }
        }
        return lineas;
    }

    private static final double ESPACIO = 0.5;

    private static double anchoDe(String palabra) {
        double total = 0;
        for (int i = 0; i < palabra.length(); ) {
            int caracter = palabra.codePointAt(i);
            total += anchoDe(caracter);
            i += Character.charCount(caracter);
        }
        return total;
    }

    /**
     * El ancho de una letra en negrita, en anchos de cifra. Por grupos y redondeado hacia
     * arriba dentro de cada grupo: la «m» y la «W» son casi el doble de una cifra, las
     * mayúsculas algo más de una, y la «i» o el punto, la mitad.
     */
    private static double anchoDe(int caracter) {
        if ("iljI.,;:'!|`".indexOf(caracter) >= 0) {
            return 0.5;
        }
        if ("frt()[]{}-/\\\"".indexOf(caracter) >= 0) {
            return 0.75;
        }
        if ("mwMW@%".indexOf(caracter) >= 0) {
            return 1.8;
        }
        if (Character.isSupplementaryCodePoint(caracter)) {
            // Un emoji o un ideograma: más ancho que cualquier letra.
            return 2.0;
        }
        if (Character.isUpperCase(caracter)) {
            return 1.35;
        }
        // Minúsculas, con tilde o sin ella, cifras y lo demás.
        return 1.05;
    }
}
