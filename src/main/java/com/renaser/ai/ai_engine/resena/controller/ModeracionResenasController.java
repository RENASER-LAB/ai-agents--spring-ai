package com.renaser.ai.ai_engine.resena.controller;

import com.renaser.ai.ai_engine.resena.dto.DtosResena.ReporteParaModerar;
import com.renaser.ai.ai_engine.resena.dto.DtosResena.ResolverReporte;
import com.renaser.ai.ai_engine.resena.service.ServicioModeracionResenas;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * «Reseñas reportadas», en Configuración (V63). Doble llave: el permiso
 * {@code moderar_resenas} y ser la plataforma, que comprueba el servicio.
 */
@RestController
@RequestMapping("/api/v1/panel/resenas-reportadas")
@RequiredArgsConstructor
@Tag(name = "Panel · Reseñas reportadas", description = "La plataforma revisa las reseñas y "
        + "las respuestas reportadas: mantener u ocultar, con una nota")
public class ModeracionResenasController {

    private final ServicioModeracionResenas servicio;
    private final Permisos permisos;

    @GetMapping
    @PreAuthorize("@permisos.tiene('moderar_resenas')")
    @Operation(summary = "Los reportes pendientes, del más antiguo al más reciente; con "
            + "resueltos=true, los resueltos, el último arriba")
    public List<ReporteParaModerar> reportes(
            @RequestParam(defaultValue = "false") boolean resueltos) {
        return servicio.reportes(permisos.actual(), resueltos);
    }

    @PostMapping("/{id}/resolucion")
    @PreAuthorize("@permisos.tiene('moderar_resenas')")
    @Operation(summary = "Mantener u ocultar lo reportado, con una nota de la revisión "
            + "obligatoria. Ocultar es definitivo")
    public ReporteParaModerar resolver(@PathVariable Long id,
                                       @RequestBody ResolverReporte datos) {
        return servicio.resolver(permisos.actual(), id, datos);
    }
}
