package com.renaser.ai.ai_engine.prueba.controller;

import com.renaser.ai.ai_engine.prueba.dto.DtosCalificacionPrueba.*;
import com.renaser.ai.ai_engine.prueba.service.ServicioCalificacionPrueba;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Calificar la prueba de un candidato, criterio a criterio.
 *
 * <p>El permiso reutiliza {@code ajustar_nota} del hito 2: en ambos casos es una persona
 * poniendo o corrigiendo una nota, y no conviene abrir un permiso nuevo para lo mismo.
 */
@RestController
@RequestMapping("/api/v1/panel/postulaciones/{postulacionId}/prueba")
@RequiredArgsConstructor
@Tag(name = "Panel · Calificación de la prueba", description = "Poner nota a cada criterio de la rúbrica")
public class CalificacionPruebaController {

    private final ServicioCalificacionPrueba servicio;
    private final Permisos permisos;

    @GetMapping("/notas")
    @PreAuthorize("@permisos.tiene('ajustar_nota')")
    public List<NotaCriterioResponse> verNotas(@PathVariable Long postulacionId) {
        return servicio.verNotas(permisos.actual(), postulacionId);
    }

    @PostMapping("/calificacion-ia")
    @PreAuthorize("@permisos.tiene('ajustar_nota')")
    @Operation(summary = "Pedirle al agente PRUEBA_PUESTO que califique los criterios que la "
            + "rúbrica le reserva. Tarda decenas de segundos y no pisa ningún ajuste a mano")
    public CalificacionIaEncolada calificarConIa(@PathVariable Long postulacionId) {
        return servicio.calificarConIa(permisos.actual(), postulacionId);
    }

    @PostMapping("/criterios/{criterioId}/nota")
    @PreAuthorize("@permisos.tiene('ajustar_nota')")
    @Operation(summary = "Poner o corregir la nota de un criterio. La explicación es obligatoria")
    public void ponerNota(@PathVariable Long postulacionId, @PathVariable Long criterioId,
                          @Valid @RequestBody PonerNotaCriterio datos) {
        servicio.ponerNota(permisos.actual(), postulacionId, criterioId, datos);
    }

    @PostMapping("/calificacion")
    @PreAuthorize("@permisos.tiene('ajustar_nota')")
    @Operation(summary = "Ponderar las notas ya puestas. Exige que estén todos los criterios de la rúbrica")
    public Map<String, BigDecimal> calcular(@PathVariable Long postulacionId) {
        return Map.of("nota", servicio.calcularNotaEtapa(permisos.actual(), postulacionId));
    }
}
