package com.renaser.ai.ai_engine.perfilintegral.controller;

import com.renaser.ai.ai_engine.perfilintegral.service.ServicioPlantillasEvaluacion;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPlantillaEvaluacion.*;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/panel/plantillas-evaluacion")
@RequiredArgsConstructor
@Tag(name = "Panel · Plantillas de evaluación", description = "La receta: qué preguntas le tocan a cada nivel y familia")
public class PlantillasEvaluacionController {

    private final ServicioPlantillasEvaluacion servicio;
    private final Permisos permisos;

    @GetMapping
    @PreAuthorize("@permisos.tiene('elegir_plantilla_evaluacion')")
    public List<PlantillaResponse> listar() {
        return servicio.listar(permisos.actual());
    }

    @PostMapping
    @PreAuthorize("@permisos.tiene('editar_plantillas_evaluacion')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear una plantilla en borrador")
    public Map<String, Long> crear(@Valid @RequestBody CrearPlantilla datos) {
        return Map.of("id", servicio.crear(permisos.actual(), datos));
    }

    @PostMapping("/{id}/publicacion")
    @PreAuthorize("@permisos.tiene('editar_plantillas_evaluacion')")
    @Operation(summary = "Publicar: ya no admite más cuotas, queda lista para usarse en una vacante")
    public void publicar(@PathVariable Long id) {
        servicio.publicar(permisos.actual(), id);
    }

    @GetMapping("/{id}/cuotas")
    @PreAuthorize("@permisos.tiene('elegir_plantilla_evaluacion')")
    public List<CuotaResponse> cuotas(@PathVariable Long id) {
        return servicio.listarCuotas(permisos.actual(), id);
    }

    @PostMapping("/{id}/cuotas")
    @PreAuthorize("@permisos.tiene('editar_plantillas_evaluacion')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agregar una cuota: cuántas preguntas de un tipo/dimensión le tocan")
    public Map<String, Long> agregarCuota(@PathVariable Long id, @Valid @RequestBody CrearCuota datos) {
        return Map.of("id", servicio.agregarCuota(permisos.actual(), id, datos));
    }
}
