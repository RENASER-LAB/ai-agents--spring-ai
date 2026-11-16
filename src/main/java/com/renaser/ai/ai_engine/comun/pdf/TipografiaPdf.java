package com.renaser.ai.ai_engine.comun.pdf;

import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.fontbox.ttf.CmapLookup;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Las fuentes de UN documento: Onest para el texto, Syne para la voz, y la parte «latin-ext» de
 * Onest para lo que las otras dos no tienen.
 *
 * <p><b>Se incrustan, y solo los glifos que se usan</b> (subconjunto). El PDF se lee igual en
 * cualquier equipo, con o sin las fuentes instaladas, y pesa decenas de kilobytes y no cientos.
 *
 * <p><b>Ningún carácter rompe el documento.</b> Lo que ninguna de las tres sabe dibujar —un emoji,
 * un carácter de control pegado desde otro programa— se cambia por un signo de interrogación en
 * vez de dejar que PDFBox lance una excepción a mitad del informe. Los saltos de línea los decide
 * quien parte el texto en renglones ({@link RenglonesPdf}), no la fuente.
 *
 * <p>Los bytes de cada archivo se leen del classpath una sola vez; lo que sí es por documento es
 * la fuente de PDFBox, porque el subconjunto se arma al guardar ese documento. Las cierra el
 * propio documento al cerrarse.
 */
public final class TipografiaPdf {

    private static final String ONEST = "pdf/fuentes/onest-latin.ttf";
    private static final String ONEST_EXT = "pdf/fuentes/onest-latin-ext.ttf";
    private static final String SYNE = "pdf/fuentes/syne-latin.ttf";
    private static final Map<String, byte[]> BYTES = new HashMap<>();

    /** El grosor del trazo que simula el peso medio, en fracción del tamaño de la letra. */
    static final float TRAZO_MEDIO = 0.035f;

    private final Cara texto;
    private final Cara textoExt;
    private final Cara voz;

    private TipografiaPdf(Cara texto, Cara textoExt, Cara voz) {
        this.texto = texto;
        this.textoExt = textoExt;
        this.voz = voz;
    }

    public static TipografiaPdf para(PDDocument documento) {
        return new TipografiaPdf(Cara.cargar(documento, ONEST), Cara.cargar(documento, ONEST_EXT),
                Cara.cargar(documento, SYNE));
    }

    /** Un trozo de texto que se escribe entero con la misma fuente. */
    public record Tramo(PDType0Font fuente, String texto) {}

    /**
     * El texto partido en tramos según qué fuente sabe dibujar cada carácter. Ya en mayúsculas si
     * el estilo lo pide, y sin caracteres de control.
     */
    public List<Tramo> tramos(String crudo, EstiloPdf estilo) {
        String texto = limpiar(crudo, estilo.mayusculas());
        Cara principal = estilo.familia() == EstiloPdf.Familia.VOZ ? voz : this.texto;
        List<Tramo> tramos = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        Cara caraActual = null;
        for (int i = 0; i < texto.length(); ) {
            int cp = texto.codePointAt(i);
            i += Character.charCount(cp);
            Cara cara;
            String trozo;
            if (principal.tiene(cp)) {
                cara = principal;
                trozo = new String(Character.toChars(cp));
            } else if (this.texto.tiene(cp)) {
                cara = this.texto;
                trozo = new String(Character.toChars(cp));
            } else if (textoExt.tiene(cp)) {
                cara = textoExt;
                trozo = new String(Character.toChars(cp));
            } else {
                cara = principal;
                trozo = "?";
            }
            if (caraActual != null && cara != caraActual) {
                tramos.add(new Tramo(caraActual.fuente, actual.toString()));
                actual.setLength(0);
            }
            caraActual = cara;
            actual.append(trozo);
        }
        if (caraActual != null && !actual.isEmpty()) {
            tramos.add(new Tramo(caraActual.fuente, actual.toString()));
        }
        return tramos;
    }

    /** El ancho en puntos de un texto de UNA línea con este estilo. */
    public float ancho(String texto, EstiloPdf estilo) {
        float total = 0f;
        int letras = 0;
        for (Tramo tramo : tramos(texto, estilo)) {
            try {
                total += tramo.fuente().getStringWidth(tramo.texto()) / 1000f * estilo.tamano();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            letras += tramo.texto().codePointCount(0, tramo.texto().length());
        }
        return total + letras * estilo.espaciado() * estilo.tamano();
    }

    /** Lo que sube la letra sobre la línea base, para colocar un renglón por su borde de arriba. */
    public float ascenso(EstiloPdf estilo) {
        return estilo.tamano() * 0.8f;
    }

    /**
     * Sin controles ni tabuladores: un {@code \t} o un {@code \u0007} pegados desde otro programa
     * no tienen glifo y harían reventar la fuente. Los saltos de línea se quitan también: aquí
     * solo llega una línea ya partida.
     */
    static String limpiar(String crudo, boolean mayusculas) {
        if (crudo == null) {
            return "";
        }
        StringBuilder limpio = new StringBuilder(crudo.length());
        for (int i = 0; i < crudo.length(); i++) {
            char c = crudo.charAt(i);
            if (c == '\t' || c == '\n' || c == '\r') {
                limpio.append(' ');
            } else if (!Character.isISOControl(c) && Character.getType(c) != Character.FORMAT) {
                limpio.append(c);
            }
        }
        String salida = limpio.toString();
        return mayusculas ? salida.toUpperCase(Locale.forLanguageTag("es")) : salida;
    }


    /** Una fuente cargada: la de PDFBox para escribir y la tabla de caracteres para preguntar. */
    private static final class Cara {
        private final PDType0Font fuente;
        private final CmapLookup caracteres;
        private final Map<Integer, Boolean> sabe = new HashMap<>();

        private Cara(PDType0Font fuente, CmapLookup caracteres) {
            this.fuente = fuente;
            this.caracteres = caracteres;
        }

        static Cara cargar(PDDocument documento, String ruta) {
            try {
                TrueTypeFont ttf = new TTFParser().parse(new RandomAccessReadBuffer(bytes(ruta)));
                PDType0Font fuente = PDType0Font.load(documento, ttf, true);
                return new Cara(fuente, ttf.getUnicodeCmapLookup());
            } catch (IOException e) {
                throw new UncheckedIOException("No se pudo cargar la fuente " + ruta, e);
            }
        }

        boolean tiene(int cp) {
            if (cp == ' ') {
                return true;
            }
            return sabe.computeIfAbsent(cp, c -> caracteres.getGlyphId(c) > 0);
        }

    }

    private static synchronized byte[] bytes(String ruta) throws IOException {
        byte[] guardados = BYTES.get(ruta);
        if (guardados != null) {
            return guardados;
        }
        try (InputStream entrada = TipografiaPdf.class.getClassLoader().getResourceAsStream(ruta)) {
            if (entrada == null) {
                throw new IOException("No está en el classpath: " + ruta);
            }
            byte[] leidos = entrada.readAllBytes();
            BYTES.put(ruta, leidos);
            return leidos;
        }
    }
}
