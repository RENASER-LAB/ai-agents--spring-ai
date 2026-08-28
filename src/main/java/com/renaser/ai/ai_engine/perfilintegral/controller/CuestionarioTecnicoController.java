package com.renaser.ai.ai_engine.perfilintegral.controller;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCuestionarioTecnico.CorregirPreguntaTecnica;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCuestionarioTecnico.CuestionarioResponse;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioCuestionarioTecnico;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// El cuestionario técnico vive bajo la vacante y con los permisos de la vacante: el dueño
// solo toca el de las SUYAS. Los bancos por nivel de la plataforma tienen otra puerta
// (banco-preguntas) y otros permisos, y desde aquí no se llega a ellos.
@RestController
@RequestMapping("/api/v1/panel/vacantes/{vacanteId}/cuestionario-tecnico")
@RequiredArgsConstructor
@Tag(name = "Panel · Cuestionario técnico",
        description = "La etapa 2 del método CAZATALENTOS: el REDACTOR propone desde la ficha, el dueño corrige y publica")
public class CuestionarioTecnicoController {

    private final ServicioCuestionarioTecnico servicio;
    private final Permisos permisos;

    @GetMapping
    @PreAuthorize("@permisos.tiene('ver_vacantes')")
    @Operation(summary = "El cuestionario de la vacante: el borrador si hay, si no la "
            + "publicada, con el estado de la generación y si quedó desactualizado")
    public CuestionarioResponse ver(@PathVariable Long vacanteId) {
        return servicio.ver(permisos.actual(), vacanteId);
    }

    @PostMapping("/generacion")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Pedir al REDACTOR el borrador. Exige la ficha COMPLETA; si ya hay "
            + "una generación en curso o la IA está apagada, encolada=false")
    public Map<String, Boolean> generar(@PathVariable Long vacanteId) {
        return Map.of("encolada", servicio.generar(permisos.actual(), vacanteId));
    }

    @PutMapping("/preguntas/{preguntaId}")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Corregir una pregunta del borrador con las palabras del dueño")
    public void corregirPregunta(@PathVariable Long vacanteId, @PathVariable Long preguntaId,
                                 @Valid @RequestBody CorregirPreguntaTecnica datos) {
        servicio.corregirPregunta(permisos.actual(), vacanteId, preguntaId, datos);
    }

    @PostMapping("/publicacion")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Publicar el borrador: el acto humano que vuelve real el "
            + "cuestionario. Vuelve a pasar la aduana entera y archiva la publicada anterior "
            + "de esta vacante")
    public void publicar(@PathVariable Long vacanteId) {
        servicio.publicar(permisos.actual(), vacanteId);
    }
}
