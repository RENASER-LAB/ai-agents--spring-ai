package com.renaser.ai.ai_engine.comun.pdf;

import java.util.ArrayList;
import java.util.List;

/**
 * Parte un texto en renglones que caben en un ancho.
 *
 * <p><b>Nada se recorta.</b> Ni «…», ni la última línea cortada: todo el texto sale, en tantos
 * renglones como haga falta. Los saltos de línea que escribió quien lo redactó se respetan, y una
 * palabra más larga que el renglón entero —una URL, un código— se parte por letras en vez de
 * salirse del margen.
 */
public final class RenglonesPdf {

    private RenglonesPdf() {
    }

    public static List<String> partir(TipografiaPdf tipografia, String texto, EstiloPdf estilo,
                                      float ancho) {
        List<String> salida = new ArrayList<>();
        if (texto == null || texto.isBlank()) {
            return salida;
        }
        String normalizado = texto.replace("\r\n", "\n").replace('\r', '\n').strip();
        for (String parrafo : normalizado.split("\n", -1)) {
            partirParrafo(tipografia, parrafo.strip(), estilo, ancho, salida);
        }
        // Dos saltos seguidos dejan un renglón vacío entre párrafos; más de uno no aporta nada.
        List<String> sinHuecosDobles = new ArrayList<>(salida.size());
        for (String linea : salida) {
            boolean vacia = linea.isEmpty();
            if (vacia && (sinHuecosDobles.isEmpty()
                    || sinHuecosDobles.get(sinHuecosDobles.size() - 1).isEmpty())) {
                continue;
            }
            sinHuecosDobles.add(linea);
        }
        return sinHuecosDobles;
    }

    private static void partirParrafo(TipografiaPdf tipografia, String parrafo, EstiloPdf estilo,
                                      float ancho, List<String> salida) {
        if (parrafo.isEmpty()) {
            salida.add("");
            return;
        }
        StringBuilder linea = new StringBuilder();
        for (String palabra : parrafo.split(" ")) {
            if (palabra.isEmpty()) {
                continue;
            }
            String candidata = linea.isEmpty() ? palabra : linea + " " + palabra;
            if (tipografia.ancho(candidata, estilo) <= ancho) {
                linea.setLength(0);
                linea.append(candidata);
                continue;
            }
            if (!linea.isEmpty()) {
                salida.add(linea.toString());
                linea.setLength(0);
            }
            if (tipografia.ancho(palabra, estilo) <= ancho) {
                linea.append(palabra);
            } else {
                // Una palabra que no cabe sola: se parte por letras.
                for (int i = 0; i < palabra.length(); ) {
                    int cp = palabra.codePointAt(i);
                    String letra = new String(Character.toChars(cp));
                    if (!linea.isEmpty() && tipografia.ancho(linea + letra, estilo) > ancho) {
                        salida.add(linea.toString());
                        linea.setLength(0);
                    }
                    linea.append(letra);
                    i += Character.charCount(cp);
                }
            }
        }
        if (!linea.isEmpty()) {
            salida.add(linea.toString());
        }
    }
}
