package com.renaser.ai.ai_engine.ai.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalControllerAdvice {

    private static final String ERRORS_BASE_URI = "https://api.motoragentes.com/errors/";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFoundException(ResourceNotFoundException ex, WebRequest request) {
        log.warn("Recurso no encontrado - Path: {}, Message: {}", request.getDescription(false), ex.getMessage());

        ProblemDetail problemDetail = buildProblemDetail(
                HttpStatus.NOT_FOUND, "Recurso no encontrado", "not-found", ex.getMessage());
        problemDetail.setProperty("Resource", ex.getResourceName());
        problemDetail.setProperty("Field", ex.getFieldName());
        problemDetail.setProperty("Value", ex.getFieldValue());

        return problemDetail;
    }

    @ExceptionHandler(ReviewNotAllowedException.class)
    public ProblemDetail handleReviewNotAllowedException(ReviewNotAllowedException ex, WebRequest request) {
        log.warn("Reseña no permitida - Path: {}, Message: {}", request.getDescription(false), ex.getMessage());

        return buildProblemDetail(HttpStatus.FORBIDDEN, "Operación no permitida", "forbidden", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        ProblemDetail problemDetail = buildProblemDetail(HttpStatus.BAD_REQUEST, "Error de validación",
                "error-validation", "La validación falló en uno o más campos");

        Map<String, String> errorMap = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errorMap.put(e.getField(), e.getDefaultMessage()));
        problemDetail.setProperty("errors", errorMap);

        return problemDetail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleException(Exception ex, WebRequest request) {
        log.warn("Ha ocurrido un error inesperado - Path: {}, Message: {}",
                request.getDescription(false), ex.getMessage(), ex);

        return buildProblemDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "internal",
                "Ha ocurrido un error inesperado. Por favor contactar con el administrador");
    }

    private ProblemDetail buildProblemDetail(HttpStatus status, String title, String typeSuffix, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        problemDetail.setType(URI.create(ERRORS_BASE_URI + typeSuffix));
        problemDetail.setProperty("Timestamp", Instant.now());
        return problemDetail;
    }
}
