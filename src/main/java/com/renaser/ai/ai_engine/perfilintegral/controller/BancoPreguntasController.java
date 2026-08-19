package com.renaser.ai.ai_engine.perfilintegral.controller;

import com.renaser.ai.ai_engine.perfilintegral.service.ServicioBancoPreguntas;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosBancoPreguntas.*;
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
@RequestMapping("/api/v1/panel/banco-preguntas")
@RequiredArgsConstructor
@Tag(name = "Panel · Banco de preguntas", description = "Repositorio de preguntas por versión; cada vacante elige de aquí")
public class BancoPreguntasController {

    private final ServicioBancoPreguntas servicio;
    private final Permisos permisos;

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
    @Operation(summary = "Publicar: la versión queda cerrada, ya no admite más preguntas")
    public void publicarVersion(@PathVariable Long id) {
        servicio.publicarVersion(permisos.actual(), id);
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
    @Operation(summary = "Agregar una opción a una pregunta cerrada")
    public Map<String, Long> agregarOpcion(@PathVariable Long id, @Valid @RequestBody CrearOpcion datos) {
        return Map.of("id", servicio.agregarOpcion(permisos.actual(), id, datos));
    }
}
