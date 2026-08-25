package com.renaser.ai.ai_engine.ai.controller;

import com.renaser.ai.ai_engine.ai.rag.DocumentIngestionService;
import com.renaser.ai.ai_engine.ai.rag.DocumentRetrievalService;
import com.renaser.ai.ai_engine.ai.rag.IngestRequest;
import com.renaser.ai.ai_engine.ai.rag.SearchResultResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
@Validated
public class RagController {

    private final DocumentIngestionService documentIngestionService;
    private final DocumentRetrievalService documentRetrievalService;

    /**
     * Ingiere un PDF del servidor en el almacén vectorial.
     *
     * <p>La ruta llega como texto y la resuelve el servicio, que la ata a
     * {@code renaser.rag.directorio-base}. Aquí no se construye ningún {@code Path}: hacerlo
     * en el controlador fue justo el fallo — {@code Path.of(request.path())} con un nulo
     * reventaba en NPE, y con {@code /etc/passwd} leía el fichero.
     */
    @PostMapping("/ingest")
    public ResponseEntity<Void> ingest(@Valid @RequestBody IngestRequest request) {
        documentIngestionService.ingestPdf(request.path());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<SearchResultResponse>> search(@RequestParam @NotBlank String query) {
        return ResponseEntity.ok(documentRetrievalService.search(query));
    }
}
