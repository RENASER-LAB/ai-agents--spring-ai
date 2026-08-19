package com.renaser.ai.ai_engine.comun.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * El CV tiene tope de 10 MB: el error debe decirlo, no responder un 500 mudo.
 *
 * <p>Está en su propia clase, y sin filtro por paquete, por una razón concreta: este error se
 * lanza al leer el multipart, <b>antes</b> de que se sepa qué método lo iba a atender. Un advice
 * limitado a un paquete no puede aplicarse cuando todavía no hay método, y el error acabaría
 * como un 500. Alcanza también al módulo de agentes, pero ahí un 413 en vez de un 500 tampoco
 * estorba: es más claro para cualquiera que suba un archivo de más.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ManejadorErrorArchivoGrande {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail archivoDemasiadoGrande(MaxUploadSizeExceededException ex) {
        return ManejadorErrores.construir(HttpStatus.CONTENT_TOO_LARGE, "Archivo demasiado grande",
                "archivo-grande", "El archivo supera el tamaño máximo permitido (10 MB)");
    }
}
