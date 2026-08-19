package com.renaser.ai.ai_engine.prueba.controller;

import com.renaser.ai.ai_engine.prueba.dto.DtosPrueba.*;
import com.renaser.ai.ai_engine.prueba.service.ServicioPrueba;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * La prueba del puesto, desde el portal del candidato.
 *
 * <p>Sin {@code @PreAuthorize}: como en la evaluación del hito 2, lo que decide aquí no es un
 * permiso sino de quién es la postulación, y eso lo comprueba el servicio con 404, nunca 403.
 */
@RestController
@RequestMapping("/api/v1/portal")
@RequiredArgsConstructor
@Tag(name = "Portal · Prueba del puesto", description = "La prueba cronometrada que rinde el candidato")
public class PruebaPortalController {

    private final ServicioPrueba servicio;
    private final Permisos permisos;

    @GetMapping("/prueba/{uuid}")
    @Operation(summary = "Mi prueba: enunciado, entregables pedidos y mis respuestas")
    public MiPrueba ver(@PathVariable UUID uuid) {
        return servicio.ver(permisos.actual(), uuid);
    }

    @PostMapping("/prueba/{uuid}/inicio")
    @Operation(summary = "Empezar. Arranca el reloj: no se detiene, ni siquiera cerrando la página")
    public MiPrueba iniciar(@PathVariable UUID uuid) {
        return servicio.iniciar(permisos.actual(), uuid);
    }

    @PutMapping("/prueba/{uuid}/respuestas/{preguntaId}")
    @Operation(summary = "Responder una pregunta previa o posterior")
    public void responder(@PathVariable UUID uuid, @PathVariable Long preguntaId,
                          @Valid @RequestBody Responder datos) {
        servicio.responder(permisos.actual(), uuid, preguntaId, datos);
    }

    @PostMapping(value = "/prueba/{uuid}/entregables/{entregableRequeridoId}/archivo",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Subir un entregable como archivo")
    public void subirArchivo(@PathVariable UUID uuid, @PathVariable Long entregableRequeridoId,
                             @RequestParam("archivo") MultipartFile archivo) {
        servicio.subirEntregableArchivo(permisos.actual(), uuid, entregableRequeridoId, archivo);
    }

    @PostMapping("/prueba/{uuid}/entregables/{entregableRequeridoId}/enlace")
    @Operation(summary = "Entregar un enlace en vez de un archivo")
    public void subirEnlace(@PathVariable UUID uuid, @PathVariable Long entregableRequeridoId,
                            @Valid @RequestBody SubirEntregableEnlace datos) {
        servicio.subirEntregableEnlace(permisos.actual(), uuid, entregableRequeridoId, datos);
    }

    @PostMapping("/prueba/{uuid}/entrega")
    @Operation(summary = "Entregar. Exige que estén todos los entregables obligatorios")
    public EntregaResponse entregar(@PathVariable UUID uuid) {
        return servicio.entregar(permisos.actual(), uuid);
    }
}
