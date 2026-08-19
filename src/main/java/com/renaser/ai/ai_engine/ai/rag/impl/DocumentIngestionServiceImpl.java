package com.renaser.ai.ai_engine.ai.rag.impl;

import com.renaser.ai.ai_engine.ai.rag.DocumentIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class DocumentIngestionServiceImpl implements DocumentIngestionService {

    private final VectorStore vectorStore;
    private final TokenTextSplitter tokenTextSplitter;

    @Override
    public void ingestPdf(Path pdfPath) {
        // Extract -> Transform -> Load, expresado como composición de funciones,
        // no como pasos imperativos sueltos.
        Supplier<List<Document>> extract = new PagePdfDocumentReader(new FileSystemResource(pdfPath));
        Function<List<Document>, List<Document>> transform = tokenTextSplitter;

        vectorStore.accept(transform.apply(extract.get()));
    }
}
