package com.renaser.ai.ai_engine.resena.controller;

import com.renaser.ai.ai_engine.resena.dto.DtosResena.EscribirRespuesta;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.MisResenas;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.Reportar;
import com.renaser.ai.ai_engine.resena.service.ServicioResenasPortal;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las reseñas desde el perfil de la persona reseñada (V63).
 *
 * <p>Sin {@code @PreAuthorize}, como el resto del perfil: lo que decide es de quién es la
 * reseña, y eso lo comprueba el servicio con la persona que pregunta. Lo ajeno es un 404.
 */
@RestController
@RequestMapping("/api/v1/portal/resenas")
@RequiredArgsConstructor
@Tag(name = "Portal · Reseñas", description = "Lo que opinan de ti las empresas que te "
        + "contrataron: leerlo, responderlo y reportarlo")
public class ResenasPortalController {

    private final ServicioResenasPortal servicio;
    private final Permisos permisos;

    @GetMapping
    @Operation(summary = "Mis reseñas visibles, las más recientes arriba, con el resumen, mi "
            + "respuesta y lo que puedo hacer con cada una")
    public MisResenas mias() {
        return servicio.mias(permisos.actual());
    }

    @PostMapping("/{id}/reporte")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Reportar una reseña mía. Sigue visible y contando mientras se "
            + "revisa; 409 si ya está reportada")
    public void reportar(@PathVariable Long id, @RequestBody Reportar datos) {
        servicio.reportar(permisos.actual(), id, datos);
    }

    @PostMapping("/{id}/respuesta")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Responder a una reseña mía: de 30 a 500 caracteres, una por reseña. "
            + "404 si la reseña ya no está disponible")
    public void responder(@PathVariable Long id, @RequestBody EscribirRespuesta datos) {
        servicio.responder(permisos.actual(), id, datos);
    }

    @PutMapping("/{id}/respuesta")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Editar mi respuesta dentro de su plazo (409 pasado el plazo)")
    public void editarRespuesta(@PathVariable Long id, @RequestBody EscribirRespuesta datos) {
        servicio.editarRespuesta(permisos.actual(), id, datos);
    }

    @DeleteMapping("/{id}/respuesta")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Borrar mi respuesta dentro de su plazo: puedo volver a responder")
    public void borrarRespuesta(@PathVariable Long id) {
        servicio.borrarRespuesta(permisos.actual(), id);
    }
}
