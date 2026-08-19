package com.renaser.ai.ai_engine.prueba.controller;

import com.renaser.ai.ai_engine.prueba.dto.DtosPlantillaPrueba.*;
import com.renaser.ai.ai_engine.prueba.service.ServicioPlantillaPrueba;
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
@RequestMapping("/api/v1/panel/plantillas-prueba")
@RequiredArgsConstructor
@Tag(name = "Panel · Plantillas de prueba", description = "La prueba del puesto: enunciado, entregables y rúbrica")
public class PlantillaPruebaController {

    private final ServicioPlantillaPrueba servicio;
    private final Permisos permisos;

    @GetMapping
    @PreAuthorize("@permisos.tiene('elegir_plantilla_prueba')")
    public List<PlantillaResponse> listar() {
        return servicio.listarPlantillas(permisos.actual());
    }

    @PostMapping
    @PreAuthorize("@permisos.tiene('editar_plantillas_prueba')")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> crear(@Valid @RequestBody CrearPlantilla datos) {
        return Map.of("id", servicio.crearPlantilla(permisos.actual(), datos));
    }

    @PostMapping("/{id}/versiones")
    @PreAuthorize("@permisos.tiene('editar_plantillas_prueba')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear una versión en borrador")
    public Map<String, Long> crearVersion(@PathVariable Long id, @Valid @RequestBody CrearVersion datos) {
        return Map.of("id", servicio.crearVersion(permisos.actual(), id, datos));
    }

    @GetMapping("/versiones/{versionId}")
    @PreAuthorize("@permisos.tiene('elegir_plantilla_prueba')")
    @Operation(summary = "La versión completa: enunciado, variantes, preguntas, entregables y rúbrica")
    public VersionCompleta verVersion(@PathVariable Long versionId) {
        return servicio.verVersion(permisos.actual(), versionId);
    }

    @PostMapping("/versiones/{versionId}/publicacion")
    @PreAuthorize("@permisos.tiene('editar_plantillas_prueba')")
    @Operation(summary = "Publicar: exige 8-10 universales, 3-5 específicas, y la rúbrica sumando 100")
    public void publicar(@PathVariable Long versionId) {
        servicio.publicarVersion(permisos.actual(), versionId);
    }

    @PostMapping("/versiones/{versionId}/variantes")
    @PreAuthorize("@permisos.tiene('editar_plantillas_prueba')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Una forma posible del cambio inesperado")
    public Map<String, Long> agregarVariante(@PathVariable Long versionId, @Valid @RequestBody CrearVariante datos) {
        return Map.of("id", servicio.agregarVariante(permisos.actual(), versionId, datos));
    }

    @GetMapping("/preguntas")
    @PreAuthorize("@permisos.tiene('elegir_plantilla_prueba')")
    @Operation(summary = "El catálogo de preguntas, opcionalmente filtrado por tipo")
    public List<PreguntaPruebaResponse> preguntas(@RequestParam(required = false) String tipo) {
        return servicio.listarPreguntasCatalogo(tipo);
    }

    @PostMapping("/preguntas")
    @PreAuthorize("@permisos.tiene('editar_plantillas_prueba')")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> crearPregunta(@Valid @RequestBody CrearPreguntaPrueba datos) {
        return Map.of("id", servicio.crearPreguntaCatalogo(permisos.actual(), datos));
    }

    @PostMapping("/versiones/{versionId}/preguntas")
    @PreAuthorize("@permisos.tiene('editar_plantillas_prueba')")
    @Operation(summary = "Elegir una pregunta del catálogo para esta versión")
    public void elegirPregunta(@PathVariable Long versionId, @Valid @RequestBody ElegirPregunta datos) {
        servicio.elegirPregunta(permisos.actual(), versionId, datos);
    }

    @PostMapping("/versiones/{versionId}/entregables")
    @PreAuthorize("@permisos.tiene('editar_plantillas_prueba')")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> agregarEntregable(@PathVariable Long versionId,
                                               @Valid @RequestBody CrearEntregableRequerido datos) {
        return Map.of("id", servicio.agregarEntregableRequerido(permisos.actual(), versionId, datos));
    }

    @PostMapping("/versiones/{versionId}/rubrica")
    @PreAuthorize("@permisos.tiene('editar_plantillas_prueba')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Un criterio de la rúbrica, con sus puntos y cómo se verifica")
    public Map<String, Long> agregarCriterio(@PathVariable Long versionId,
                                             @Valid @RequestBody CrearCriterioRubrica datos) {
        return Map.of("id", servicio.agregarCriterioRubrica(permisos.actual(), versionId, datos));
    }
}
