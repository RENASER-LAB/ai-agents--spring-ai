package com.renaser.ai.ai_engine.ai.rag;

import jakarta.validation.constraints.NotBlank;

/**
 * El PDF que se quiere ingerir, por su ruta en el servidor.
 *
 * <p>La ruta <b>no</b> es libre: se resuelve contra {@code renaser.rag.directorio-base} y tiene
 * que quedar dentro. Sin esa comprobación esto era una lectura arbitraria de ficheros —
 * {@code {"path":"/etc/passwd"}} se leía y su texto quedaba consultable por
 * {@code GET /api/v1/rag/search}—. Quien valida es
 * {@link com.renaser.ai.ai_engine.ai.rag.impl.DocumentIngestionServiceImpl}, no el controlador,
 * para que la guarda proteja también a cualquier otro llamador.
 */
public record IngestRequest(@NotBlank String path) {
}
