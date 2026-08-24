package com.renaser.ai.ai_engine.ai.rag.impl;

import com.renaser.ai.ai_engine.ai.exception.IngestaNoPermitidaException;
import com.renaser.ai.ai_engine.ai.rag.PropiedadesRag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * La guarda que ata la ingesta del RAG a un directorio, y por qué cada caso está aquí.
 *
 * <p>Esto nació de un fallo real: {@code POST /api/v1/rag/ingest} recibía una ruta del cuerpo
 * y la usaba tal cual, así que leía cualquier PDF del servidor y su texto quedaba consultable
 * por {@code GET /api/v1/rag/search}. Sin token, además.
 *
 * <p>Cada prueba de las de abajo cubre una forma distinta de salirse del directorio. No son
 * variaciones de la misma: {@code base.resolve(x)} descarta la base con una ruta absoluta, y
 * {@code normalize()} no ve los enlaces simbólicos. Una comprobación ingenua pasa una y falla
 * la otra, y por eso están las dos escritas.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("La ingesta del RAG solo lee de su directorio")
class DocumentIngestionServiceImplTest {

    @Mock private VectorStore vectorStore;
    @Mock private TokenTextSplitter splitter;

    @TempDir Path permitido;

    private DocumentIngestionServiceImpl servicio;
    private PropiedadesRag propiedades;

    @BeforeEach
    void configurar() {
        propiedades = new PropiedadesRag();
        propiedades.setDirectorioBase(permitido.toString());
        servicio = new DocumentIngestionServiceImpl(vectorStore, splitter, propiedades);
    }

    @Test
    @DisplayName("una ruta absoluta de fuera se rechaza, y no se toca el almacén")
    void unaRutaAbsolutaDeFueraSeRechaza() {
        assertThatThrownBy(() -> servicio.ingestPdf("/etc/passwd"))
                .isInstanceOf(IngestaNoPermitidaException.class);

        // Lo que de verdad importa: nada de fuera llegó a indexarse
        verifyNoInteractions(vectorStore);
    }

    @Test
    @DisplayName("subir con «..» hasta salirse del directorio se rechaza")
    void subirConDosPuntosSeRechaza() {
        assertThatThrownBy(() -> servicio.ingestPdf("../../etc/passwd"))
                .isInstanceOf(IngestaNoPermitidaException.class);
        verifyNoInteractions(vectorStore);
    }

    @Test
    @DisplayName("bajar y volver a subir tampoco cuela")
    void bajarYVolverASubirTampocoCuela() throws IOException {
        Files.createDirectory(permitido.resolve("dentro"));

        assertThatThrownBy(() -> servicio.ingestPdf("dentro/../../fuera.pdf"))
                .isInstanceOf(IngestaNoPermitidaException.class);
        verifyNoInteractions(vectorStore);
    }

    @Test
    @DisplayName("un enlace simbólico que apunta afuera se rechaza: normalize() no lo ve")
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void unEnlaceQueApuntaAfueraSeRechaza(@TempDir Path fuera) throws IOException {
        Path secreto = Files.writeString(fuera.resolve("secreto.pdf"), "no deberia leerse");
        Files.createSymbolicLink(permitido.resolve("parece-de-dentro.pdf"), secreto);

        assertThatThrownBy(() -> servicio.ingestPdf("parece-de-dentro.pdf"))
                .isInstanceOf(IngestaNoPermitidaException.class);
        verifyNoInteractions(vectorStore);
    }

    @Test
    @DisplayName("un fichero que no existe se rechaza con el mismo mensaje que uno de fuera")
    void unFicheroQueNoExisteNoSeDistingueDeUnoDeFuera() {
        // Que los dos mensajes coincidan es la prueba: si difirieran, el endpoint diría
        // qué ficheros hay en el servidor a quien fuera preguntando uno por uno
        String noExiste = mensajeDe(() -> servicio.ingestPdf("no-existe.pdf"));
        String deFuera = mensajeDe(() -> servicio.ingestPdf("/etc/passwd"));

        assertThat(noExiste).isEqualTo(deFuera);
    }

    @Test
    @DisplayName("sin directorio configurado la ingesta está apagada")
    void sinDirectorioConfiguradoLaIngestaEstaApagada() {
        propiedades.setDirectorioBase(null);

        assertThatThrownBy(() -> servicio.ingestPdf("cualquiera.pdf"))
                .isInstanceOf(IngestaNoPermitidaException.class)
                .hasMessageContaining("no está habilitada");
        verifyNoInteractions(vectorStore);
    }

    @Test
    @DisplayName("una ruta nula se rechaza, y no revienta en NullPointerException")
    void unaRutaNulaSeRechaza() {
        // Por HTTP lo para @NotBlank, pero la guarda promete valer para cualquier llamador y
        // Path.of(null) lanza NPE, que habría acabado en un 500
        assertThatThrownBy(() -> servicio.ingestPdf(null))
                .isInstanceOf(IngestaNoPermitidaException.class);
        verifyNoInteractions(vectorStore);
    }

    @Test
    @DisplayName("un directorio no es un documento")
    void unDirectorioNoEsUnDocumento() throws IOException {
        Files.createDirectory(permitido.resolve("carpeta"));

        assertThatThrownBy(() -> servicio.ingestPdf("carpeta"))
                .isInstanceOf(IngestaNoPermitidaException.class);
        verifyNoInteractions(vectorStore);
    }

    @Test
    @DisplayName("un PDF de dentro del directorio se ingiere")
    void unPdfDeDentroSeIngiere() throws IOException {
        try (PDDocument documento = new PDDocument()) {
            documento.addPage(new PDPage());
            documento.save(permitido.resolve("bueno.pdf").toFile());
        }

        servicio.ingestPdf("bueno.pdf");

        // El camino feliz completo: la guarda deja pasar y lo leído llega al almacén
        verify(vectorStore).accept(any());
    }

    @Test
    @DisplayName("un fichero de dentro que no es un PDF es un rechazo, no una avería")
    void unFicheroDeDentroQueNoEsPdfEsUnRechazo() throws IOException {
        Files.writeString(permitido.resolve("disfrazado.pdf"), "esto no es un pdf");

        // Está donde debe, así que el cerco lo deja pasar; lo para el lector. Tiene que salir
        // como rechazo (400) y no subir a GlobalControllerAdvice, que lo convertiría en 500
        assertThatThrownBy(() -> servicio.ingestPdf("disfrazado.pdf"))
                .isInstanceOf(IngestaNoPermitidaException.class);
        verifyNoInteractions(vectorStore);
    }

    private String mensajeDe(Runnable accion) {
        try {
            accion.run();
            throw new AssertionError("Se esperaba un rechazo y no lo hubo");
        } catch (IngestaNoPermitidaException e) {
            return e.getMessage();
        }
    }
}
