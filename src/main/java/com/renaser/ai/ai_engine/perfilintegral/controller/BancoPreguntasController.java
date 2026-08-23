package com.renaser.ai.ai_engine.perfilintegral.controller;

import com.renaser.ai.ai_engine.perfilintegral.service.ServicioBancoPreguntas;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioImportacionBanco;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosBancoPreguntas.*;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosImportacionBanco.ResultadoImportacion;
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

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/panel/banco-preguntas")
@RequiredArgsConstructor
@Tag(name = "Panel · Banco de preguntas", description = "Repositorio de preguntas por versión; cada vacante elige de aquí")
public class BancoPreguntasController {

    private final ServicioBancoPreguntas servicio;
    private final ServicioImportacionBanco importacion;
    private final Permisos permisos;

    // ---------- Importar desde Excel ----------

    @PostMapping(value = "/importaciones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permisos.tiene('editar_banco_preguntas')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Importar un banco desde la plantilla Excel: crea una versión en "
            + "borrador para revisar y publicar; si el archivo tiene problemas, 400 con "
            + "la lista completa y no se importa nada")
    public ResultadoImportacion importar(@RequestParam("archivo") MultipartFile archivo,
                                         @RequestParam String nivelPuestoCodigo,
                                         @RequestParam String etiqueta) throws IOException {
        return importacion.importar(permisos.actual(), nivelPuestoCodigo, etiqueta,
                archivo.getOriginalFilename(), archivo.getBytes());
    }

    @GetMapping("/dimensiones")
    @PreAuthorize("@permisos.tiene('ver_banco_preguntas')")
    @Operation(summary = "El catálogo de dimensiones: lo que vale en la columna «Qué mide»")
    public List<DimensionResponse> dimensiones() {
        return importacion.listarDimensiones(permisos.actual());
    }

    // ---------- Versiones ----------

    @GetMapping("/versiones")
    @PreAuthorize("@permisos.tiene('ver_banco_preguntas')")
    @Operation(summary = "Las versiones visibles: las propias más la biblioteca global")
    public List<VersionBancoResponse> versiones() {
        return servicio.listarVersiones(permisos.actual());
    }

    @PostMapping("/versiones")
    @PreAuthorize("@permisos.tiene('editar_banco_preguntas')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear una versión del banco, en borrador")
    public Map<String, Long> crearVersion(@Valid @RequestBody CrearVersionBanco datos) {
        return Map.of("id", servicio.crearVersion(permisos.actual(), datos));
    }

    @PostMapping("/versiones/{id}/publicacion")
    @PreAuthorize("@permisos.tiene('publicar_version_banco')")
    @Operation(summary = "Publicar: valida la coherencia de cada formato, cierra la versión y archiva a la que reemplaza")
    public void publicarVersion(@PathVariable Long id) {
        servicio.publicarVersion(permisos.actual(), id);
    }

    @PostMapping("/versiones/{id}/archivado")
    @PreAuthorize("@permisos.tiene('publicar_version_banco')")
    @Operation(summary = "Archivar: la versión deja de asignarse; quien no empezó pasa al banco vigente, quien empezó conserva el suyo")
    public void archivarVersion(@PathVariable Long id) {
        servicio.archivarVersion(permisos.actual(), id);
    }

    // ---------- Preguntas ----------

    @GetMapping("/versiones/{id}/preguntas")
    @PreAuthorize("@permisos.tiene('ver_banco_preguntas')")
    public List<PreguntaResponse> preguntas(@PathVariable Long id) {
        return servicio.listarPreguntas(permisos.actual(), id);
    }

    @PostMapping("/versiones/{id}/preguntas")
    @PreAuthorize("@permisos.tiene('editar_banco_preguntas')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agregar una pregunta a una versión en borrador")
    public Map<String, Long> crearPregunta(@PathVariable Long id, @Valid @RequestBody CrearPregunta datos) {
        return Map.of("id", servicio.crearPregunta(permisos.actual(), id, datos));
    }

    // ---------- Opciones ----------

    @GetMapping("/preguntas/{id}/opciones")
    @PreAuthorize("@permisos.tiene('ver_banco_preguntas')")
    public List<OpcionResponse> opciones(@PathVariable Long id) {
        return servicio.listarOpciones(permisos.actual(), id);
    }

    @PostMapping("/preguntas/{id}/opciones")
    @PreAuthorize("@permisos.tiene('editar_banco_preguntas')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agregar una opción a una pregunta de una versión en borrador")
    public Map<String, Long> agregarOpcion(@PathVariable Long id, @Valid @RequestBody CrearOpcion datos) {
        return Map.of("id", servicio.agregarOpcion(permisos.actual(), id, datos));
    }

    // ---------- Los tramos de los ítems V ----------

    @GetMapping("/preguntas/{id}/rangos")
    @PreAuthorize("@permisos.tiene('ver_banco_preguntas')")
    public List<RangoResponse> rangos(@PathVariable Long id) {
        return servicio.listarRangos(permisos.actual(), id);
    }

    @PostMapping("/preguntas/{id}/rangos")
    @PreAuthorize("@permisos.tiene('editar_banco_preguntas')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agregar un tramo de puntaje a un ítem V en borrador")
    public Map<String, Long> agregarRango(@PathVariable Long id, @Valid @RequestBody CrearRango datos) {
        return Map.of("id", servicio.agregarRango(permisos.actual(), id, datos));
    }

    // ---------- Los campos de los casos descompuestos ----------

    @GetMapping("/preguntas/{id}/campos-caso")
    @PreAuthorize("@permisos.tiene('ver_banco_preguntas')")
    public List<CampoCasoResponse> camposCaso(@PathVariable Long id) {
        return servicio.listarCamposCaso(permisos.actual(), id);
    }

    @PostMapping("/preguntas/{id}/campos-caso")
    @PreAuthorize("@permisos.tiene('editar_banco_preguntas')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agregar un campo a un caso descompuesto (CD) en borrador")
    public Map<String, Long> agregarCampoCaso(@PathVariable Long id, @Valid @RequestBody CrearCampoCaso datos) {
        return Map.of("id", servicio.agregarCampoCaso(permisos.actual(), id, datos));
    }

    // ---------- Los pares de consistencia ----------

    @GetMapping("/versiones/{id}/pares-consistencia")
    @PreAuthorize("@permisos.tiene('ver_banco_preguntas')")
    public List<ParConsistenciaResponse> paresConsistencia(@PathVariable Long id) {
        return servicio.listarParesConsistencia(permisos.actual(), id);
    }

    @PostMapping("/versiones/{id}/pares-consistencia")
    @PreAuthorize("@permisos.tiene('editar_banco_preguntas')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Emparejar dos preguntas de la versión para vigilar contradicciones")
    public Map<String, Long> agregarParConsistencia(@PathVariable Long id,
                                                    @Valid @RequestBody CrearParConsistencia datos) {
        return Map.of("id", servicio.agregarParConsistencia(permisos.actual(), id, datos));
    }
}
