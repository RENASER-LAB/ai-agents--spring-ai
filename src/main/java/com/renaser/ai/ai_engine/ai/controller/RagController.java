package com.renaser.ai.ai_engine.ai.controller;

import com.renaser.ai.ai_engine.ai.rag.DocumentIngestionService;
import com.renaser.ai.ai_engine.ai.rag.DocumentRetrievalService;
import com.renaser.ai.ai_engine.ai.rag.IngestRequest;
import com.renaser.ai.ai_engine.ai.rag.SearchResultResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
public class RagController {

    private final DocumentIngestionService documentIngestionService;
    private final DocumentRetrievalService documentRetrievalService;

    @PostMapping("/ingest")
    public ResponseEntity<Void> ingest(@RequestBody IngestRequest request) {
        documentIngestionService.ingestPdf(Path.of(request.path()));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<SearchResultResponse>> search(@RequestParam String query) {
        return ResponseEntity.ok(documentRetrievalService.search(query));
    }
}
