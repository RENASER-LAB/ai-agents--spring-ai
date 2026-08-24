package com.renaser.ai.ai_engine.ai.rag.impl;

import com.renaser.ai.ai_engine.ai.exception.IngestaNoPermitidaException;
import com.renaser.ai.ai_engine.ai.rag.DocumentIngestionService;
import com.renaser.ai.ai_engine.ai.rag.PropiedadesRag;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class DocumentIngestionServiceImpl implements DocumentIngestionService {

    /** Un solo mensaje para todos los rechazos: distinguirlos delata qué ficheros hay. */
    private static final String RECHAZO = "Esa ruta no apunta a un documento que se pueda ingerir";

    private final VectorStore vectorStore;
    private final TokenTextSplitter tokenTextSplitter;
    private final PropiedadesRag propiedades;

    @Override
    public void ingestPdf(String rutaRelativa) {
        Path pdf = resolverDentroDelDirectorioPermitido(rutaRelativa);

        // Extract -> Transform -> Load, expresado como composición de funciones,
        // no como pasos imperativos sueltos.
        Supplier<List<Document>> extract = new PagePdfDocumentReader(new FileSystemResource(pdf));
        Function<List<Document>, List<Document>> transform = tokenTextSplitter;

        vectorStore.accept(transform.apply(extract.get()));
    }

    /**
     * Ata la ruta pedida al directorio permitido, o rechaza.
     *
     * <p>Es la guarda que faltaba. Antes la ruta iba directa a {@code Path.of(...)} y de ahí a
     * un {@code FileSystemResource}, que resuelve contra el directorio de trabajo del proceso:
     * cualquier ruta absoluta valía.
     *
     * <p>Tres trampas que este orden evita, y por las que no basta con «comprobar que empieza
     * por el directorio base»:
     * <ol>
     *   <li>{@code base.resolve(x)} <b>descarta base</b> si {@code x} es absoluta. Por eso se
     *       rechaza lo absoluto antes de resolver, y no se confía solo en el prefijo.
     *   <li>{@code normalize()} no sigue enlaces simbólicos: un symlink dentro del directorio
     *       apuntando afuera pasaría. Por eso se comprueba también sobre {@code toRealPath()}.
     *   <li>Comprobar la existencia antes que el encierro convierte el endpoint en un detector
     *       de ficheros del servidor. Por eso el encierro va primero, y el mensaje es único.
     * </ol>
     */
    private Path resolverDentroDelDirectorioPermitido(String rutaRelativa) {
        String configurado = propiedades.getDirectorioBase();
        if (configurado == null || configurado.isBlank()) {
            // Apagado por defecto y a propósito: ver PropiedadesRag
            throw new IngestaNoPermitidaException(
                    "La ingesta de documentos por ruta no está habilitada en esta instalación");
        }

        Path base;
        try {
            base = Path.of(configurado).toRealPath();
        } catch (IOException | InvalidPathException e) {
            // Configuración mala, no petición mala: que se vea como avería y no como un 400
            throw new IllegalStateException(
                    "El directorio de documentos del RAG no existe: " + configurado, e);
        }

        Path candidato;
        try {
            candidato = Path.of(rutaRelativa);
        } catch (InvalidPathException e) {
            throw new IngestaNoPermitidaException(RECHAZO);
        }
        if (candidato.isAbsolute()) {
            throw new IngestaNoPermitidaException(RECHAZO);
        }

        Path resuelto = base.resolve(candidato).normalize();
        if (!resuelto.startsWith(base)) {
            throw new IngestaNoPermitidaException(RECHAZO);
        }
        if (!Files.isRegularFile(resuelto) || !Files.isReadable(resuelto)) {
            throw new IngestaNoPermitidaException(RECHAZO);
        }

        Path real;
        try {
            real = resuelto.toRealPath();
        } catch (IOException e) {
            throw new IngestaNoPermitidaException(RECHAZO);
        }
        if (!real.startsWith(base)) {
            // Un enlace simbólico que apunta fuera. normalize() no lo habría visto.
            throw new IngestaNoPermitidaException(RECHAZO);
        }
        return real;
    }
}
