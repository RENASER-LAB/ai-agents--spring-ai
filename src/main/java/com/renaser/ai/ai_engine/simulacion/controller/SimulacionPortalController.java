package com.renaser.ai.ai_engine.simulacion.controller;

import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacion.MiSesion;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacion.SesionDisponible;
import com.renaser.ai.ai_engine.simulacion.service.ServicioSimulacion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * La simulación desde el portal del candidato: qué fechas puede elegir y cuál eligió.
 *
 * <p>Solo ve las sesiones de <b>su</b> vacante que todavía tengan cupo. Y no ve la matriz de
 * información crítica: es justamente lo que se espera que descubra o pregunte por su cuenta.
 */
@RestController
@RequestMapping("/api/v1/portal")
@RequiredArgsConstructor
@Tag(name = "Portal · Simulación", description = "Elegir la fecha de la sesión de trabajo")
public class SimulacionPortalController {

    private final ServicioSimulacion servicio;
    private final Permisos permisos;

    @GetMapping("/simulacion/{uuid}/sesiones")
    @PreAuthorize("@permisos.tiene('elegir_sesion_simulacion')")
    @Operation(summary = "Las fechas disponibles para mi vacante, con las plazas que quedan")
    public List<SesionDisponible> disponibles(@PathVariable UUID uuid) {
        return servicio.sesionesDisponibles(permisos.actual(), uuid);
    }

    @PostMapping("/simulacion/{uuid}/sesiones/{sesionId}")
    @PreAuthorize("@permisos.tiene('elegir_sesion_simulacion')")
    @Operation(summary = "Elegir una fecha")
    public MiSesion inscribirse(@PathVariable UUID uuid, @PathVariable Long sesionId) {
        return servicio.inscribirse(permisos.actual(), uuid, sesionId);
    }

    @GetMapping("/simulacion/{uuid}")
    @PreAuthorize("@permisos.tiene('elegir_sesion_simulacion')")
    @Operation(summary = "La sesión que elegí, con su reparto de minutos")
    public MiSesion miSesion(@PathVariable UUID uuid) {
        return servicio.miSesion(permisos.actual(), uuid);
    }
}
