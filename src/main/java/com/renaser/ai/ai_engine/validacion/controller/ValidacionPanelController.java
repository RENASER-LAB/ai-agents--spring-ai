package com.renaser.ai.ai_engine.validacion.controller;

import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.validacion.dto.DtosValidacion.*;
import com.renaser.ai.ai_engine.validacion.service.ServicioValidacion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/panel/postulaciones/{postulacionId}/validacion")
@RequiredArgsConstructor
@Tag(name = "Panel · Validación práctica", description = "El periodo de trabajo antes de decidir")
public class ValidacionPanelController {

    private final ServicioValidacion servicio;
    private final Permisos permisos;

    @GetMapping
    @PreAuthorize("@permisos.tiene('completar_metricas_validacion')")
    public ValidacionResponse ver(@PathVariable Long postulacionId) {
        return servicio.ver(permisos.actual(), postulacionId);
    }

    @PostMapping("/habilitacion")
    @PreAuthorize("@permisos.tiene('habilitar_validacion')")
    @Operation(summary = "Fijar modalidad y días. El trabajo real exige la figura contractual")
    public void habilitar(@PathVariable Long postulacionId, @Valid @RequestBody HabilitarValidacion datos) {
        servicio.habilitar(permisos.actual(), postulacionId, datos);
    }

    @PostMapping("/inicio")
    @PreAuthorize("@permisos.tiene('iniciar_validacion')")
    @Operation(summary = "Arrancar el periodo: fija el inicio y el fin")
    public void iniciar(@PathVariable Long postulacionId) {
        servicio.iniciar(permisos.actual(), postulacionId);
    }

    @GetMapping("/metricas")
    @PreAuthorize("@permisos.tiene('completar_metricas_validacion')")
    @Operation(summary = "Las nueve métricas, con lo que ya está puesto y de dónde salió")
    public List<MetricaResponse> verMetricas(@PathVariable Long postulacionId) {
        return servicio.verMetricas(permisos.actual(), postulacionId);
    }

    @PostMapping("/metricas/{criterioId}")
    @PreAuthorize("@permisos.tiene('completar_metricas_validacion')")
    @Operation(summary = "Completar una métrica que no se alimentó sola. La explicación es obligatoria")
    public void completar(@PathVariable Long postulacionId, @PathVariable Long criterioId,
                          @Valid @RequestBody CompletarMetrica datos) {
        servicio.completarMetrica(permisos.actual(), postulacionId, criterioId, datos);
    }

    @PostMapping("/cierre")
    @PreAuthorize("@permisos.tiene('cerrar_validacion')")
    @Operation(summary = "Cerrar: pondera las métricas y pasa a la decisión final")
    public void cerrar(@PathVariable Long postulacionId) {
        servicio.cerrar(permisos.actual(), postulacionId);
    }
}
