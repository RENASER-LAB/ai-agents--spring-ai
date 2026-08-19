package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.postulacion.service.impl.ExtractorTextoCv;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La limpieza del texto del currículum antes de que salga de aquí.
 *
 * <p>El caso que de verdad importa es el byte nulo: un PDF mal generado lo trae, una columna
 * de texto de Postgres lo rechaza, y la calificación se estrellaba <b>al guardar</b>, con la
 * nota ya calculada y ya pagada. Peor todavía, el reintento se estrellaba igual, porque el
 * archivo no cambia entre un intento y el siguiente.
 *
 * <p>Se prueba por la vía del .docx —que no es más que un zip con un XML dentro, y se puede
 * armar aquí mismo— porque es la única forma de meter un byte nulo en el texto de entrada
 * sin arrastrar un archivo de ejemplo al repositorio.
 */
class ExtractorTextoCvTest {

    // Los caracteres que nunca deberían llegar a la base ni al modelo. Se nombran así
    // porque escritos dentro de la cadena no se ven, y una prueba que no se lee no sirve.
    private static final String NULO = String.valueOf((char) 0x00);
    private static final String CAMPANA = String.valueOf((char) 0x07);
    private static final String SEPARADOR = String.valueOf((char) 0x1F);
    private static final String BORRAR = String.valueOf((char) 0x7F);

    private final ExtractorTextoCv extractor = new ExtractorTextoCv();

    @Test
    void elByteNuloNoLlegaAlTextoQueSeGuarda() {
        byte[] docx = docxCon("Camila Rojas" + NULO + " Ingeniera industrial" + NULO
                + " con ocho años de experiencia");

        String texto = extractor.extraer(docx, null, "cv.docx");

        // Si esto se cuela, la fila no entra en la base y la nota recién calculada se pierde.
        assertThat(texto).doesNotContain(NULO);
        assertThat(texto)
                .isEqualTo("Camila Rojas Ingeniera industrial con ocho años de experiencia");
    }

    @Test
    void tambienSeVanLosDemasCaracteresDeControl() {
        // No solo el nulo: cualquier exportador descuidado mete alguno, y ninguno significa
        // nada en un currículum, ni para quien lo lee ni para el modelo.
        byte[] docx = docxCon("Camila Rojas" + CAMPANA + " con ocho años" + SEPARADOR
                + " en logística de planta" + BORRAR);

        String texto = extractor.extraer(docx, null, "cv.docx");

        assertThat(texto).isEqualTo("Camila Rojas con ocho años en logística de planta");
    }

    @Test
    void loQueSiPuntuaSobreviveALaLimpieza() {
        // Limpiar de más sería tan malo como no limpiar: la nota saldría baja por un motivo
        // que no tiene nada que ver con el candidato.
        String texto = extractor.extraer(docxCon("""
                Camila Rojas
                EXPERIENCIA
                Automaticé el cierre mensual y pasó de 3 días a 4 horas."""), null, "cv.docx");

        assertThat(texto).contains("Camila Rojas");
        assertThat(texto).contains("Automaticé el cierre mensual y pasó de 3 días a 4 horas.");
    }

    @Test
    void unCurriculumQueSoloTraeBasuraSeRechazaEnVezDeMandarseAlModelo() {
        // Cincuenta caracteres de control se quedan en cero caracteres útiles. Vale más que
        // el trabajo falle aquí que pagar una llamada al modelo para puntuar la nada.
        assertThatThrownBy(() -> extractor.extraer(docxCon(NULO.repeat(50)), null, "cv.docx"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no tiene texto");
    }

    @Test
    void unTextoDemasiadoCortoNoEsUnCurriculum() {
        assertThatThrownBy(() -> extractor.extraer(docxCon("Camila Rojas"), null, "cv.docx"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void losEspaciosYLosSaltosDeMasSeCompactan() {
        String texto = extractor.extraer(
                docxCon("Camila     Rojas\n\n\n\n\nIngeniera industrial de planta y proyectos"),
                null, "cv.docx");

        assertThat(texto).isEqualTo("Camila Rojas\n\nIngeniera industrial de planta y proyectos");
    }

    @Test
    void elDocAntiguoSeRechazaDiciendoQueHacer() {
        // No se devuelve texto a medias: un currículum leído a medias produce una nota
        // injusta y el candidato nunca se entera de por qué.
        assertThatThrownBy(() -> extractor.extraer(new byte[] {1, 2, 3}, null, "curriculum.doc"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("curriculum.doc")
                .hasMessageContaining("PDF");
    }

    /** Un .docx de mentira: el zip mínimo con el único archivo que el extractor mira. */
    private byte[] docxCon(String texto) {
        StringBuilder xml = new StringBuilder("<w:document><w:body>");
        texto.lines().forEach(linea ->
                xml.append("<w:p><w:r><w:t>").append(linea).append("</w:t></w:r></w:p>"));
        xml.append("</w:body></w:document>");

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(xml.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return salida.toByteArray();
    }
}
