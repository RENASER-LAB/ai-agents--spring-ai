package com.renaser.ai.ai_engine.comun.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los renglones y las fuentes del PDF, medidos con las fuentes de verdad: nada se recorta, una
 * palabra que no cabe se parte por letras, y un carácter que ninguna fuente sabe dibujar sale
 * como «?» en vez de reventar el documento.
 */
@DisplayName("Los renglones y las fuentes del PDF")
class RenglonesPdfTest {

    private static final EstiloPdf TEXTO = EstiloPdf.texto(10f, Color.BLACK);

    private PDDocument documento;
    private TipografiaPdf tipografia;

    @BeforeEach
    void abrir() {
        documento = new PDDocument();
        tipografia = TipografiaPdf.para(documento);
    }

    @AfterEach
    void cerrar() throws IOException {
        documento.close();
    }

    @Test
    @DisplayName("sin texto no hay renglones")
    void sinTexto() {
        assertThat(RenglonesPdf.partir(tipografia, null, TEXTO, 100f)).isEmpty();
        assertThat(RenglonesPdf.partir(tipografia, " \n ", TEXTO, 100f)).isEmpty();
    }

    @Test
    @DisplayName("parte por palabras donde ya no cabe, sin perder ninguna ni dejar espacios dobles")
    void partePorPalabras() {
        float ancho = tipografia.ancho("uno dos", TEXTO) + 0.5f;

        List<String> renglones = RenglonesPdf.partir(tipografia, "uno  dos uno dos uno", TEXTO, ancho);

        assertThat(renglones).containsExactly("uno dos", "uno dos", "uno");
    }

    @Test
    @DisplayName("respeta los saltos de quien lo escribió y deja como mucho un renglón vacío entre párrafos")
    void saltosDeLinea() {
        List<String> renglones = RenglonesPdf.partir(tipografia, "\r\nPrimero\r\n\r\n\r\nSegundo\rTercero",
                TEXTO, 500f);

        assertThat(renglones).containsExactly("Primero", "", "Segundo", "Tercero");
    }

    @Test
    @DisplayName("una palabra más larga que el renglón entero se parte por letras y sale entera")
    void palabraLarga() {
        String url = "https://renaser.pe/una/ruta/muy/larga";
        float ancho = tipografia.ancho("https://", TEXTO);

        List<String> renglones = RenglonesPdf.partir(tipografia, "Ver " + url, TEXTO, ancho);

        assertThat(renglones.get(0)).isEqualTo("Ver");
        assertThat(String.join("", renglones.subList(1, renglones.size()))).isEqualTo(url);
        assertThat(renglones).allSatisfy(r -> assertThat(tipografia.ancho(r, TEXTO)).isLessThanOrEqualTo(ancho));
    }

    @Test
    @DisplayName("cada carácter va con la fuente que lo sabe dibujar, y el que ninguna sabe sale como «?»")
    void tramosPorFuente() {
        List<TipografiaPdf.Tramo> tramos = tipografia.tramos("Año ő 中", TEXTO);

        assertThat(tramos).extracting(TipografiaPdf.Tramo::texto).containsExactly("Año ", "ő", " ?");
        assertThat(tramos.get(1).fuente()).isNotSameAs(tramos.get(0).fuente());
        assertThat(tramos.get(2).fuente()).isSameAs(tramos.get(0).fuente());
        assertThat(tipografia.tramos("", TEXTO)).isEmpty();
    }

    @Test
    @DisplayName("la voz va en mayúsculas y con espaciado, así que ocupa más que el mismo texto normal")
    void voz() {
        EstiloPdf voz = EstiloPdf.voz(10f, Color.BLACK);

        assertThat(tipografia.tramos("informe", voz)).extracting(TipografiaPdf.Tramo::texto)
                .containsExactly("INFORME");
        assertThat(tipografia.ancho("informe", voz)).isGreaterThan(tipografia.ancho("informe", TEXTO));
        assertThat(tipografia.ancho("", voz)).isZero();
        assertThat(tipografia.ascenso(voz)).isEqualTo(8f);
    }

    @Test
    @DisplayName("los tabuladores y saltos se vuelven espacios, y los controles y caracteres invisibles se quitan")
    void limpiar() {
        assertThat(TipografiaPdf.limpiar(null, false)).isEmpty();
        assertThat(TipografiaPdf.limpiar("a\tb\nc\rd\u0007e​f", false)).isEqualTo("a b c def");
        assertThat(TipografiaPdf.limpiar("ñandú", true)).isEqualTo("ÑANDÚ");
    }

    @Test
    @DisplayName("los estilos derivados cambian solo lo que dicen")
    void estilos() {
        EstiloPdf grande = TEXTO.conTamano(20f).medio();

        assertThat(grande.tamano()).isEqualTo(20f);
        assertThat(grande.media()).isTrue();
        assertThat(grande.normal().media()).isFalse();
        assertThat(grande.normal().tamano()).isEqualTo(20f);
        assertThat(TEXTO.interlineado(1.5f)).isEqualTo(15f);
        assertThat(TEXTO.conColor(Color.RED).color()).isEqualTo(Color.RED);
        assertThat(TEXTO.espaciado(0.1f).enMayusculas().mayusculas()).isTrue();
    }
}
