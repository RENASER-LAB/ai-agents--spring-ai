package com.renaser.ai.ai_engine.ai.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.renaser.ai.ai_engine.comun.exception.ManejadorErrores;

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

    /**
     * Una dirección que no existe es un 404, no un 500.
     *
     * <p>Sin este método la pedía el {@code @ExceptionHandler(Exception.class)} de abajo y
     * todo lo desconocido salía como «Ha ocurrido un error inesperado». {@link ManejadorErrores}
     * ya lo resolvía bien, pero está acotado con {@code basePackageClasses} a los controladores
     * del portal y del panel: una ruta sin controlador no cae en ninguno, así que llegaba aquí.
     *
     * <p>No es cosmético. Durante la auditoría del Sprint 1, {@code GET /actuator/health}
     * devolvió 500 y pareció que el backend estaba caído cuando estaba sano; y el
     * {@code CLAUDE.md} del portal ya avisaba de lo mismo con {@code /api/vacantes} en vez de
     * {@code /api/v1/portal/vacantes}. Un 500 obliga a mirar los registros del servidor; un
     * 404 se entiende leyéndolo.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail rutaInexistente(NoResourceFoundException ex, WebRequest request) {
        log.debug("Ruta inexistente - Path: {}", request.getDescription(false));

        return buildProblemDetail(HttpStatus.NOT_FOUND, "Esa dirección no existe", "ruta-inexistente",
                "No hay nada en «" + ex.getResourcePath() + "». Revisa la ruta: la base de la API "
                        + "es /api/v1/portal para el candidato y /api/v1/panel para el equipo");
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
