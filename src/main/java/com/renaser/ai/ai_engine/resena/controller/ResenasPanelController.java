package com.renaser.ai.ai_engine.resena.controller;

import com.renaser.ai.ai_engine.resena.dto.DtosResena.EscribirResena;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.LaResenaDeMiEmpresa;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.Reportar;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.ResenasDeLaPostulacion;
import com.renaser.ai.ai_engine.resena.service.ServicioResenasPanel;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * Las reseñas en la ficha del postulante (V63).
 *
 * <p>Leer pide {@code ver_resenas_candidato} o {@code resenar_contratado}: quien solo puede
 * escribir ve igual el bloque de su empresa, y la respuesta dice con dos booleanos qué
 * pintar. Escribir pide {@code resenar_contratado}, y el alcance lo aplica el servicio.
 */
@RestController
@RequestMapping("/api/v1/panel/postulaciones/{id}")
@RequiredArgsConstructor
@Tag(name = "Panel · Reseñas", description = "La reseña de la empresa a quien contrató, y las "
        + "de las demás empresas. No puntúan: no entran en notas, ranking ni IA")
public class ResenasPanelController {

    private final ServicioResenasPanel servicio;
    private final Permisos permisos;

    @GetMapping("/resenas")
    @PreAuthorize("@permisos.tiene('ver_resenas_candidato') or @permisos.tiene('resenar_contratado')")
    @Operation(summary = "Las reseñas de la persona de esta postulación (resumen y lista, solo "
            + "con ver_resenas_candidato) y el bloque de la reseña de mi empresa (solo con "
            + "resenar_contratado y la postulación en CONTRATADO)")
    public ResenasDeLaPostulacion ver(@PathVariable Long id) {
        return servicio.ver(permisos.actual(), id);
    }

    @PostMapping("/resena")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permisos.tiene('resenar_contratado')")
    @Operation(summary = "Publicar la reseña: estrellas enteras de 1 a 5 y opinión de 30 a "
            + "1000 caracteres. Desde los 30 días de la contratación; una por contratación "
            + "(409 si ya hay una)")
    public LaResenaDeMiEmpresa publicar(@PathVariable Long id,
                                        @RequestBody EscribirResena datos) {
        return servicio.publicar(permisos.actual(), id, datos);
    }

    @PutMapping("/resena")
    @PreAuthorize("@permisos.tiene('resenar_contratado')")
    @Operation(summary = "Editar la reseña durante los 30 días desde su primera publicación "
            + "(409 pasado el plazo). Si ya estaba respondida, la persona recibe un aviso y "
            + "otros 30 días para ajustar su respuesta")
    public LaResenaDeMiEmpresa editar(@PathVariable Long id,
                                      @RequestBody EscribirResena datos) {
        return servicio.editar(permisos.actual(), id, datos);
    }

    @DeleteMapping("/resena")
    @PreAuthorize("@permisos.tiene('resenar_contratado')")
    @Operation(summary = "Borrar la reseña dentro de su plazo. La respuesta se va con ella y "
            + "la contratación vuelve a quedar libre")
    public LaResenaDeMiEmpresa borrar(@PathVariable Long id) {
        return servicio.borrar(permisos.actual(), id);
    }

    @PostMapping("/resena/respuesta/reporte")
    @PreAuthorize("@permisos.tiene('resenar_contratado')")
    @Operation(summary = "Reportar la respuesta de la persona a la reseña de mi empresa. Una "
            + "vez; otra más si la plataforma la mantuvo y la persona la editó después")
    public LaResenaDeMiEmpresa reportarRespuesta(@PathVariable Long id,
                                                 @RequestBody Reportar datos) {
        return servicio.reportarRespuesta(permisos.actual(), id, datos);
    }
}
