package com.renaser.ai.ai_engine.prueba.controller;

import com.renaser.ai.ai_engine.prueba.dto.DtosPlantillaPrueba.*;
import com.renaser.ai.ai_engine.prueba.service.ServicioPlantillaPrueba;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @GetMapping("/{id}/versiones")
    @PreAuthorize("@permisos.tiene('elegir_plantilla_prueba')")
    @Operation(summary = "Las versiones de esta plantilla, de la más nueva a la más vieja. "
            + "Vienen todas, borradores incluidos: el estado dice cuál se puede usar")
    public List<VersionResponse> listarVersiones(@PathVariable Long id) {
        return servicio.listarVersiones(permisos.actual(), id);
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

    // ---------- Corregir y quitar, solo en borrador ----------
    // Mientras la versión no se publica se compone entera: se cambia, se quita y se
    // reordena. Publicada, todo esto responde 409 y la salida a un error es una versión
    // nueva — no hay «despublicar», y el porqué está en el javadoc de ServicioPlantillaPrueba.

    @PutMapping("/versiones/{versionId}")
    @PreAuthorize("@permisos.tiene('editar_plantillas_prueba')")
    @Operation(summary = "Reemplazar los datos de una versión en borrador: enunciado, "
            + "materiales, herramientas, modalidad, duración, plazo y los minutos del cambio")
    public void actualizarVersion(@PathVariable Long versionId,
                                  @Valid @RequestBody CrearVersion datos) {
        servicio.actualizarVersion(permisos.actual(), versionId, datos);
    }

    @PostMapping(value = "/versiones/{versionId}/consigna",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permisos.tiene('editar_plantillas_prueba')")
    @Operation(summary = "Subir el ENUNCIADO de la prueba como archivo (PDF o Word). Es el "
            + "papel que lee el candidato y el que va enlazado en el correo: no crea "
            + "preguntas, ni entregables, ni rúbrica, y publicar sigue exigiendo lo mismo")
    public ConsignaResponse subirConsigna(@PathVariable Long versionId,
                                          @RequestParam("archivo") MultipartFile archivo) {
        return servicio.subirConsigna(permisos.actual(), versionId, archivo);
    }

    @DeleteMapping("/versiones/{versionId}/preguntas/{preguntaId}")
    @PreAuthorize("@permisos.tiene('editar_plantillas_prueba')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Quitar una pregunta de esta versión. La pregunta sigue en el "
            + "catálogo: otras versiones pueden estar usándola")
    public void quitarPregunta(@PathVariable Long versionId, @PathVariable Long preguntaId) {
        servicio.quitarPregunta(permisos.actual(), versionId, preguntaId);
    }

    @PutMapping("/entregables/{entregableId}")
    @PreAuthorize("@permisos.tiene('editar_plantillas_prueba')")
    @Operation(summary = "Reemplazar un entregable de un borrador")
    public void actualizarEntregable(@PathVariable Long entregableId,
                                     @Valid @RequestBody CrearEntregableRequerido datos) {
        servicio.actualizarEntregableRequerido(permisos.actual(), entregableId, datos);
    }

    @DeleteMapping("/entregables/{entregableId}")
    @PreAuthorize("@permisos.tiene('editar_plantillas_prueba')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Quitar un entregable de un borrador")
    public void quitarEntregable(@PathVariable Long entregableId) {
        servicio.quitarEntregableRequerido(permisos.actual(), entregableId);
    }

    @PutMapping("/rubrica/{criterioId}")
    @PreAuthorize("@permisos.tiene('editar_plantillas_prueba')")
    @Operation(summary = "Reemplazar un criterio de la rúbrica de un borrador")
    public void actualizarCriterio(@PathVariable Long criterioId,
                                   @Valid @RequestBody CrearCriterioRubrica datos) {
        servicio.actualizarCriterioRubrica(permisos.actual(), criterioId, datos);
    }

    @DeleteMapping("/rubrica/{criterioId}")
    @PreAuthorize("@permisos.tiene('editar_plantillas_prueba')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Quitar un criterio de la rúbrica de un borrador: es lo que "
            + "deshace una rúbrica que se pasó de 100 puntos")
    public void quitarCriterio(@PathVariable Long criterioId) {
        servicio.quitarCriterioRubrica(permisos.actual(), criterioId);
    }

    @PutMapping("/variantes/{varianteId}")
    @PreAuthorize("@permisos.tiene('editar_plantillas_prueba')")
    @Operation(summary = "Reemplazar el texto de una variante de un borrador")
    public void actualizarVariante(@PathVariable Long varianteId,
                                   @Valid @RequestBody CrearVariante datos) {
        servicio.actualizarVariante(permisos.actual(), varianteId, datos);
    }

    @DeleteMapping("/variantes/{varianteId}")
    @PreAuthorize("@permisos.tiene('editar_plantillas_prueba')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Quitar una variante del cambio inesperado de un borrador")
    public void quitarVariante(@PathVariable Long varianteId) {
        servicio.quitarVariante(permisos.actual(), varianteId);
    }

    @PutMapping("/versiones/{versionId}/preguntas/orden")
    @PreAuthorize("@permisos.tiene('editar_plantillas_prueba')")
    @Operation(summary = "El orden de las preguntas elegidas, la lista entera de una vez")
    public void reordenarPreguntas(@PathVariable Long versionId,
                                   @Valid @RequestBody ReordenarElementos datos) {
        servicio.reordenarPreguntas(permisos.actual(), versionId, datos);
    }

    @PutMapping("/versiones/{versionId}/entregables/orden")
    @PreAuthorize("@permisos.tiene('editar_plantillas_prueba')")
    @Operation(summary = "El orden de los entregables, la lista entera de una vez")
    public void reordenarEntregables(@PathVariable Long versionId,
                                     @Valid @RequestBody ReordenarElementos datos) {
        servicio.reordenarEntregables(permisos.actual(), versionId, datos);
    }

    @PutMapping("/versiones/{versionId}/variantes/orden")
    @PreAuthorize("@permisos.tiene('editar_plantillas_prueba')")
    @Operation(summary = "El orden de las variantes, la lista entera de una vez")
    public void reordenarVariantes(@PathVariable Long versionId,
                                   @Valid @RequestBody ReordenarElementos datos) {
        servicio.reordenarVariantes(permisos.actual(), versionId, datos);
    }

    @PutMapping("/versiones/{versionId}/rubrica/orden")
    @PreAuthorize("@permisos.tiene('editar_plantillas_prueba')")
    @Operation(summary = "El orden de los criterios de la rúbrica, la lista entera de una vez")
    public void reordenarRubrica(@PathVariable Long versionId,
                                 @Valid @RequestBody ReordenarElementos datos) {
        servicio.reordenarRubrica(permisos.actual(), versionId, datos);
    }
}
