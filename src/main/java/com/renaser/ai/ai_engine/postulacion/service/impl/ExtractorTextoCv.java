package com.renaser.ai.ai_engine.postulacion.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * De archivo a texto plano.
 *
 * <p>El portal acepta PDF y Word. Aquí se resuelven los dos formatos que de verdad llegan:
 *
 * <ul>
 *   <li><b>PDF</b> con PDFBox, que ya estaba en el proyecto (lo trae el lector de
 *       documentos de Spring AI).
 *   <li><b>.docx</b> abriéndolo como el zip que es y leyendo {@code word/document.xml}.
 *       Un .docx no es más que eso, y sacarle el texto son treinta líneas. Traer Apache POI
 *       entero —cinco librerías— solo para esto no se justificaba.
 * </ul>
 *
 * <p><b>El .doc antiguo no se puede leer</b> y es a propósito: es un formato binario de
 * los noventa que sí exigiría POI. Se avisa con un mensaje claro en vez de devolver texto
 * a medias, porque un currículum leído a medias produce una nota injusta.
 */
@Component
@Slf4j
public class ExtractorTextoCv {

    /**
     * @param tipo el content-type con el que se subió el archivo
     * @throws IllegalStateException si del archivo no se puede sacar texto
     */
    public String extraer(byte[] contenido, String tipo, String nombreOriginal) {
        String extension = extensionDe(nombreOriginal);

        if ("pdf".equals(extension) || "application/pdf".equals(tipo)) {
            return dePdf(contenido);
        }
        if ("docx".equals(extension)) {
            return deDocx(contenido);
        }
        throw new IllegalStateException(
                ("No se puede leer el texto de «%s». La IA solo puede calificar currículums en "
                        + "PDF o .docx; el formato .doc antiguo hay que volver a subirlo como PDF.")
                        .formatted(nombreOriginal));
    }

    private String dePdf(byte[] contenido) {
        try (PDDocument documento = Loader.loadPDF(contenido)) {
            // sortByPosition: sin esto un CV a dos columnas sale con las frases entrelazadas
            PDFTextStripper lector = new PDFTextStripper();
            lector.setSortByPosition(true);
            String texto = lector.getText(documento);
            return exigirContenido(texto, "El PDF no tiene texto: puede ser una foto escaneada del currículum");
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo abrir el PDF del currículum", e);
        }
    }

    /**
     * Un .docx es un zip. El texto vive en {@code word/document.xml}, y cada {@code <w:p>}
     * es un párrafo: por eso se cierra con salto de línea antes de quitar las etiquetas.
     */
    private String deDocx(byte[] contenido) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(contenido))) {
            ZipEntry entrada;
            while ((entrada = zip.getNextEntry()) != null) {
                if (!"word/document.xml".equals(entrada.getName())) {
                    continue;
                }
                String xml = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                String texto = xml
                        .replaceAll("</w:p>", "\n")
                        .replaceAll("<w:tab[^>]*/>", "\t")
                        .replaceAll("<[^>]+>", "")
                        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                        .replace("&quot;", "\"").replace("&apos;", "'");
                return exigirContenido(texto, "El documento de Word no tiene texto");
            }
            throw new IllegalStateException("El archivo .docx está dañado: no contiene el documento");
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo abrir el documento de Word del currículum", e);
        }
    }

    private String exigirContenido(String texto, String queja) {
        // El byte nulo y los demás caracteres de control se van antes que nada. Un PDF mal
        // generado los trae, y una columna de texto de Postgres los rechaza: la calificación
        // se estrellaba al guardar, con la nota ya calculada, y el reintento volvía a
        // estrellarse igual porque el archivo no cambia. Se limpian aquí y no al guardar
        // porque tampoco tienen nada que hacer en lo que se le manda al modelo.
        String sinControles = texto == null ? ""
                : texto.replaceAll("[\\x00-\\x08\\x0E-\\x1F\\x7F]", "");
        String limpio = sinControles.replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n{3,}", "\n\n").trim();
        // Un currículum de menos de 40 caracteres no es un currículum. Mejor fallar y que el
        // trabajo quede pendiente que mandarle basura al modelo y guardar la nota que salga.
        if (limpio.length() < 40) {
            throw new IllegalStateException(queja);
        }
        return limpio;
    }

    private String extensionDe(String nombre) {
        if (nombre == null) return "";
        int punto = nombre.lastIndexOf('.');
        return punto < 0 ? "" : nombre.substring(punto + 1).toLowerCase();
    }
}
